package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UploadStatus;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.occurrence.*;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.media.MediaUploadService;
import br.com.faitec.falacidade.port.service.occurrence.OccurrenceService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/occurrence")
public class OccurrenceRestController {

    private final OccurrenceService  occurrenceService;
    private final MediaUploadService mediaUploadService;
    private final UserService        userService;
    private final EmailService       emailService;

    public OccurrenceRestController(OccurrenceService occurrenceService,
                                    MediaUploadService mediaUploadService,
                                    UserService userService,
                                    EmailService emailService) {
        this.occurrenceService  = occurrenceService;
        this.mediaUploadService = mediaUploadService;
        this.userService        = userService;
        this.emailService       = emailService;
    }

    /** Envios de foto por IP por dia — o endpoint é público (visitante anexa foto sem conta). */
    private static final int MAX_UPLOADS_PER_DAY_PER_IP = 20;
    // ponytail: contador em memória com lock global; se rodar em mais de uma instância,
    // mover para o banco como em OccurrenceDao.countTodayAnonymousByIp.
    private final Map<String, Integer> uploadsByIp = new HashMap<>();
    private int uploadCounterDay = LocalDate.now().getDayOfYear();

    @PostMapping("/upload-media")
    public ResponseEntity<Map<String, String>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") Occurrence.OccurrenceType type,
            HttpServletRequest request) {
        if (file == null || file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "Nenhum arquivo enviado."));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            return ResponseEntity.badRequest().body(Map.of("error", "Envie um arquivo de imagem."));
        if (uploadLimitReached(extractClientIp(request)))
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Limite de " + MAX_UPLOADS_PER_DAY_PER_IP + " envios de foto por dia atingido."));
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Falha ao ler o arquivo."));
        }
        String uploadId = UUID.randomUUID().toString();
        mediaUploadService.uploadAsync(bytes, type, uploadId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("uploadId", uploadId,
                             "message", "Upload iniciado. Consulte /api/occurrence/upload-status/" + uploadId));
    }

    @GetMapping("/upload-status/{uploadId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable String uploadId) {
        UploadStatus status = mediaUploadService.getUploadStatus(uploadId);
        if (status == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Upload não encontrado."));
        return switch (status.getState()) {
            case PROCESSING -> ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("state", "PROCESSING"));
            case DONE       -> ResponseEntity.ok(Map.of("state", "DONE", "publicId", status.getPublicId(),
                                                        "url", status.getUrl(), "blurred", status.isBlurred()));
            case REJECTED   -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .body(Map.of("state", "REJECTED", "blurred", true, "message", status.getMessage()));
            case ERROR      -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("state", "ERROR", "message", status.getMessage()));
        };
    }

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateOccurrenceDto dto, HttpServletRequest request,
            Authentication auth) {
        // O autor vem sempre do token: o e-mail do corpo só sinaliza "quero me identificar"
        // (em branco = anônima). Sem token a ocorrência é obrigatoriamente anônima — impede
        // registrar ocorrência (e disparar e-mail) em nome de terceiros.
        boolean identified = dto.getEmail() != null && !dto.getEmail().isBlank();
        dto.setEmail(auth != null && identified ? auth.getName() : null);
        try {
            String clientIp = extractClientIp(request);
            CreateOccurrenceResponseDto response = occurrenceService.createOccurrence(dto.toOccurrence(), clientIp);
            // Confirma o registro por e-mail (só identificados; falha não interrompe o fluxo)
            if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
                try {
                    UserModel author = userService.findByEmail(dto.getEmail());
                    emailService.sendOccurrenceCreatedEmail(dto.getEmail(),
                        author != null ? author.getFullname() : null,
                        response.getProtocolNumber(), dto.getTitle());
                } catch (Exception ignored) {}
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            // RF07/RF08: diz qual limite diário estourou e quando ele é renovado — a contagem
            // é por dia do servidor (DATE(created_at) = CURRENT_DATE), logo zera à meia-noite.
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", e.getMessage() + ". Você poderá registrar novamente à meia-noite (00:00)."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetOccurrenceDto> getById(@PathVariable int id, Authentication auth) {
        GetOccurrenceDto entity = occurrenceService.findById(id);
        if (entity == null) return ResponseEntity.notFound().build();
        maskIfNotPrivileged(entity, auth);
        return ResponseEntity.ok(entity);
    }

    @GetMapping
    public ResponseEntity<List<GetOccurrenceDto>> getAll(Authentication auth) {
        // RF a.8 / b.2: o escopo da listagem depende do papel do usuário.
        // ADMINISTRATOR: todas as ocorrências (visão geral da administração pública).
        if (hasRole(auth, UserModel.UserRole.ADMINISTRATOR))
            return ResponseEntity.ok(occurrenceService.findAll());

        // RF08/RF11: visitante (sem login) vê todas as ocorrências, com autor oculto.
        if (auth == null) {
            List<GetOccurrenceDto> all = occurrenceService.findAll();
            all.forEach(this::maskAuthor);
            return ResponseEntity.ok(all);
        }

        UserModel user = safeFindUser(auth);

        // EMPLOYEE: apenas as ocorrências do município ao qual está vinculado.
        if (hasRole(auth, UserModel.UserRole.EMPLOYEE)) {
            String city = user != null ? user.getCity() : null;
            return ResponseEntity.ok(occurrenceService.findAllByCity(city));
        }

        // CITIZEN: apenas as ocorrências abertas pelo próprio usuário.
        return ResponseEntity.ok(occurrenceService.findAllByUserEmail(auth.getName()));
    }

    @GetMapping("/protocol/{number}")
    public ResponseEntity<GetOccurrenceDto> getByProtocol(@PathVariable String number, Authentication auth) {
        GetOccurrenceDto entity = occurrenceService.findByProtocolNumber(number);
        if (entity == null) return ResponseEntity.notFound().build();
        maskIfNotPrivileged(entity, auth);
        return ResponseEntity.ok(entity);
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<GetOccurrenceDto>> getNearby(
            @RequestParam double lat, @RequestParam double lon,
            @RequestParam Occurrence.OccurrenceType type) {
        List<GetOccurrenceDto> nearby = occurrenceService.findNearbyDuplicates(lat, lon, type);
        nearby.forEach(this::maskAuthor);
        return ResponseEntity.ok(nearby);
    }

    /** RF16: registra o apoio do usuário logado à ocorrência. */
    @PostMapping("/{id}/support")
    public ResponseEntity<Map<String, Object>> support(@PathVariable int id, Authentication auth) {
        UserModel user = safeFindUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        try {
            occurrenceService.supportOccurrence(id, user.getId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "count", occurrenceService.getSupportCount(id),
            "supportedByMe", true
        ));
    }

    /** RF16: total de apoios + se o usuário logado já apoiou. */
    @GetMapping("/{id}/support")
    public ResponseEntity<Map<String, Object>> supportInfo(@PathVariable int id, Authentication auth) {
        UserModel user = safeFindUser(auth);
        return ResponseEntity.ok(Map.of(
            "count", occurrenceService.getSupportCount(id),
            "supportedByMe", user != null && occurrenceService.hasSupported(id, user.getId())
        ));
    }

    @GetMapping("/anonymous-status")
    public ResponseEntity<GetOccurrenceDto> getAnonymousStatus(
            @Valid @ModelAttribute AnonymousTrackingQueryDto query) {
        GetOccurrenceDto result = occurrenceService.findByAnonymousTrackingCode(query.getTrackingCode());
        if (result == null) return ResponseEntity.notFound().build();
        maskAuthor(result);
        return ResponseEntity.ok(result);
    }

    /** Muda o status; mensagem opcional vai ao histórico e ao e-mail do autor (RF12: coletivo). */
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable int id,
            @Valid @RequestBody UpdateOccurrenceStatusDto dto, Authentication auth) {
        occurrenceService.changeStatus(id, dto.getNewStatus().name(), getUserId(auth),
            dto.getObservation(), dto.isCollective());
        return ResponseEntity.noContent().build();
    }

    /** RN03: registra quem iniciou; RF12: opcionalmente aplica ao grupo todo e notifica autores. */
    @PutMapping("/progress/{id}")
    public ResponseEntity<Void> toInProgress(@PathVariable int id,
            @RequestBody(required = false) UpdateOccurrenceStatusDto body, Authentication auth) {
        occurrenceService.changeStatus(id, Occurrence.OccurrenceStatus.EM_ANDAMENTO.name(), getUserId(auth),
            body != null ? body.getObservation() : null, body != null && body.isCollective());
        return ResponseEntity.noContent().build();
    }

    /** RN03: registra quem concluiu; RF12: opcionalmente aplica ao grupo todo e notifica autores. */
    @PutMapping("/conclude/{id}")
    public ResponseEntity<Void> toConclude(@PathVariable int id,
            @RequestBody(required = false) UpdateOccurrenceStatusDto body, Authentication auth) {
        occurrenceService.changeStatus(id, Occurrence.OccurrenceStatus.ATENDIDA.name(), getUserId(auth),
            body != null ? body.getObservation() : null, body != null && body.isCollective());
        return ResponseEntity.noContent().build();
    }

    /** RF12: ocorrências do mesmo grupo de duplicatas (autores mascarados p/ não-staff). */
    @GetMapping("/{id}/group")
    public ResponseEntity<List<GetOccurrenceDto>> getGroup(@PathVariable int id, Authentication auth) {
        List<GetOccurrenceDto> group = occurrenceService.getGroup(id);
        if (!isPrivileged(auth)) group.forEach(this::maskAuthor);
        return ResponseEntity.ok(group);
    }

    /** RN03/RF11: histórico de status; nome do responsável só para Funcionário/Administrador. */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<OccurrenceHistoryDto>> getHistory(@PathVariable int id, Authentication auth) {
        List<OccurrenceHistoryDto> history = occurrenceService.getHistory(id);
        if (!isPrivileged(auth)) history.forEach(h -> h.setChangedByName(null));
        return ResponseEntity.ok(history);
    }

    /** RF12: oculta dados do autor para usuários sem privilégio, exceto o próprio denunciante. */
    private void maskIfNotPrivileged(GetOccurrenceDto e, Authentication auth) {
        if (isPrivileged(auth)) return;
        if (auth != null && e.getEmail() != null && e.getEmail().equals(auth.getName())) return;
        maskAuthor(e);
    }

    private void maskAuthor(GetOccurrenceDto e) {
        e.setEmail(null);
        e.setFullname(null);
    }

    /** RF12: administrador ou funcionário têm acesso a dados sensíveis. */
    private boolean isPrivileged(Authentication auth) {
        return hasRole(auth, UserModel.UserRole.ADMINISTRATOR)
            || hasRole(auth, UserModel.UserRole.EMPLOYEE);
    }

    private boolean hasRole(Authentication auth, UserModel.UserRole role) {
        if (auth == null) return false;
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.contains(role.name()));
    }

    private int getUserId(Authentication auth) {
        UserModel user = safeFindUser(auth);
        return user != null ? user.getId() : 0;
    }

    /** Busca o usuário autenticado pelo e-mail (subject do JWT), tolerante a falhas. */
    private UserModel safeFindUser(Authentication auth) {
        if (auth == null) return null;
        try { return userService.findByEmail(auth.getName()); }
        catch (Exception e) { return null; }
    }

    /** Conta os envios do dia por IP; reinicia a contagem na virada do dia. */
    private synchronized boolean uploadLimitReached(String ip) {
        int today = LocalDate.now().getDayOfYear();
        if (today != uploadCounterDay) {
            uploadCounterDay = today;
            uploadsByIp.clear();
        }
        return uploadsByIp.merge(ip, 1, Integer::sum) > MAX_UPLOADS_PER_DAY_PER_IP;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank())
            return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
