package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.occurrence.DepartmentOccurrenceViewDto;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto;
import br.com.faitec.falacidade.implementation.service.department.DepartmentAccessTokenService;
import br.com.faitec.falacidade.port.service.department.DepartmentService;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.media.MediaUploadService;
import br.com.faitec.falacidade.port.service.occurrence.OccurrenceService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Página do departamento aberta pelo link do e-mail de encaminhamento. Sem login: as permissões
 * vêm no token (grupo da ocorrência + departamento). Nada de dados pessoais.
 */
@RestController
@RequestMapping("/api/department-access")
public class DepartmentAccessRestController {

    private final DepartmentAccessTokenService tokens;
    private final OccurrenceService occurrenceService;
    private final DepartmentService departmentService;
    private final MediaUploadService mediaUploadService;
    private final UserService userService;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:4173}")
    private String frontendUrl;

    public DepartmentAccessRestController(DepartmentAccessTokenService tokens, OccurrenceService occurrenceService,
                                          DepartmentService departmentService, MediaUploadService mediaUploadService,
                                          UserService userService, EmailService emailService) {
        this.tokens = tokens;
        this.occurrenceService = occurrenceService;
        this.departmentService = departmentService;
        this.mediaUploadService = mediaUploadService;
        this.userService = userService;
        this.emailService = emailService;
    }

    private record Scope(DepartmentAccessTokenService.Access access, List<GetOccurrenceDto> group,
                         GetOccurrenceDto target) {}

    @GetMapping("/occurrence")
    public ResponseEntity<?> view(@RequestHeader(value = DepartmentAccessTokenService.HEADER, required = false) String token,
                                  @RequestParam(required = false) Integer id) {
        Object scope = resolve(token, id);
        if (scope instanceof ResponseEntity<?> denied) return denied;
        Scope s = (Scope) scope;
        if (s.group().stream().noneMatch(DepartmentAccessRestController::isOpen))
            return error(HttpStatus.GONE, "Esta ocorrência foi encerrada; o acesso do departamento terminou.");

        List<OccurrenceHistoryDto> all = occurrenceService.getHistory(s.target().getId());
        LocalDateTime pending = pendingRequestAt(all, s.access().departmentId());

        DepartmentOccurrenceViewDto body = new DepartmentOccurrenceViewDto();
        Department department = departmentService.findById(s.access().departmentId());
        body.setDepartmentName(department != null ? department.getName() : null);
        body.setOccurrence(mask(s.target()));
        body.setGroup(s.group().stream().map(DepartmentAccessRestController::mask).toList());
        body.setHistory(all.stream().filter(h -> !h.isCompletionRequest())
                           .peek(h -> h.setChangedByName(null)).toList());
        body.setPendingRequestAt(pending);
        body.setCanRequestCompletion(isOpen(s.target()) && pending == null);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/completion-request")
    public ResponseEntity<?> requestCompletion(
            @RequestHeader(value = DepartmentAccessTokenService.HEADER, required = false) String token,
            @RequestParam int id, @RequestParam(required = false) String message,
            @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Object scope = resolve(token, id);
        if (scope instanceof ResponseEntity<?> denied) return denied;
        Scope s = (Scope) scope;
        if (file == null || file.isEmpty() || file.getContentType() == null
                || !file.getContentType().startsWith("image/"))
            return error(HttpStatus.BAD_REQUEST, "Envie uma foto do serviço feito.");
        if (!isOpen(s.target()))
            return error(HttpStatus.CONFLICT, "Esta ocorrência não está mais aberta.");
        if (pendingRequestAt(occurrenceService.getHistory(id), s.access().departmentId()) != null)
            return error(HttpStatus.CONFLICT, "Já existe uma solicitação aguardando confirmação da prefeitura.");

        MediaUploadService.UploadResult upload;
        try { upload = mediaUploadService.uploadSync(file.getBytes(), s.target().getType()); }
        catch (IOException | RuntimeException e) {
            return error(HttpStatus.BAD_GATEWAY, "Não foi possível enviar a foto. Tente de novo.");
        }
        if (upload == null || upload.rejected())
            return error(HttpStatus.UNPROCESSABLE_ENTITY, upload != null && upload.rejectionReason() != null
                ? upload.rejectionReason() : "A foto foi recusada. Envie outra.");

        occurrenceService.requestCompletion(id, s.access().departmentId(), message, upload.url());
        notifyForwarder(s);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // Valida o token e se a ocorrência pedida é do grupo do link; devolve Scope ou a resposta de erro.
    private Object resolve(String token, Integer id) {
        Optional<DepartmentAccessTokenService.Access> access = tokens.verify(token);
        if (access.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "Link inválido ou expirado. Peça um novo encaminhamento.");
        List<GetOccurrenceDto> group = occurrenceService.getGroup(access.get().groupRootId()).stream()
            .filter(o -> o.getStatus() != Occurrence.OccurrenceStatus.FINALIZADA).toList();
        if (group.isEmpty()) return error(HttpStatus.NOT_FOUND, "Ocorrência não encontrada.");
        // Sem id: a ocorrência encaminhada (o e-mail mostra ela), não a raiz do grupo.
        int wanted = id != null ? id : access.get().focusId();
        GetOccurrenceDto target = group.stream().filter(o -> o.getId() == wanted).findFirst()
            .orElse(id == null ? group.get(0) : null);
        if (target == null) return error(HttpStatus.NOT_FOUND, "Ocorrência não encontrada.");
        return new Scope(access.get(), group, target);
    }

    /**
     * Pendente = solicitação deste departamento mais nova que a última troca real de status
     * ou que o último encaminhamento a ele (reencaminhar é o jeito da equipe pedir "refaça").
     */
    static LocalDateTime pendingRequestAt(List<OccurrenceHistoryDto> history, int departmentId) {
        LocalDateTime lastChange = history.stream()
            .filter(h -> !h.isCompletionRequest()
                      && ((h.getOldStatus() != null && !h.getOldStatus().equals(h.getNewStatus()))
                          || Integer.valueOf(departmentId).equals(h.getDepartmentId())))
            .map(OccurrenceHistoryDto::getChangedAt).filter(java.util.Objects::nonNull)
            .max(LocalDateTime::compareTo).orElse(LocalDateTime.MIN);
        return history.stream()
            .filter(h -> h.isCompletionRequest() && Integer.valueOf(departmentId).equals(h.getDepartmentId()))
            .map(OccurrenceHistoryDto::getChangedAt).filter(java.util.Objects::nonNull)
            .filter(t -> t.isAfter(lastChange))
            .max(LocalDateTime::compareTo).orElse(null);
    }

    private void notifyForwarder(Scope s) {
        try {
            UserModel forwarder = userService.findById(s.access().forwardedBy());
            Department department = departmentService.findById(s.access().departmentId());
            if (forwarder == null || forwarder.getEmail() == null) return;
            emailService.sendCompletionRequestEmail(forwarder.getEmail(), s.target().getProtocolNumber(),
                department != null ? department.getName() : "departamento",
                appUrlOf(s) + "/occurrence/detail/" + s.target().getId());
        } catch (Exception ignored) { }  // o aviso é cortesia; a solicitação já foi gravada
    }

    // Endereço gravado no token pelo encaminhamento autenticado; o Origin deste endpoint público não vale.
    private String appUrlOf(Scope s) {
        String app = s.access().appUrl();
        return app != null && app.startsWith("http") ? app : frontendUrl;
    }

    private static boolean isOpen(GetOccurrenceDto o) {
        return o.getStatus() == Occurrence.OccurrenceStatus.PENDENTE
            || o.getStatus() == Occurrence.OccurrenceStatus.EM_ANDAMENTO;
    }

    private static GetOccurrenceDto mask(GetOccurrenceDto o) {
        o.setEmail(null);
        o.setFullname(null);
        return o;
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
