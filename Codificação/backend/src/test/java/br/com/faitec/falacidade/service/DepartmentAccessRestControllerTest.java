package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.DepartmentAccessRestController;
import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.occurrence.DepartmentOccurrenceViewDto;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto;
import br.com.faitec.falacidade.implementation.service.department.DepartmentAccessTokenService;
import br.com.faitec.falacidade.implementation.service.department.DepartmentAccessTokenService.Access;
import br.com.faitec.falacidade.port.service.department.DepartmentService;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.media.MediaUploadService;
import br.com.faitec.falacidade.port.service.occurrence.OccurrenceService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DepartmentAccessRestController – acesso do departamento pelo link")
class DepartmentAccessRestControllerTest {

    @Mock DepartmentAccessTokenService tokens;
    @Mock OccurrenceService occurrenceService;
    @Mock DepartmentService departmentService;
    @Mock MediaUploadService mediaUploadService;
    @Mock UserService userService;
    @Mock EmailService emailService;
    DepartmentAccessRestController sut;
    MockHttpServletRequest request;
    GetOccurrenceDto raiz, agrupada, deFora;

    static GetOccurrenceDto occ(int id, Occurrence.OccurrenceStatus status) {
        GetOccurrenceDto o = new GetOccurrenceDto();
        o.setId(id); o.setStatus(status); o.setProtocolNumber("FC-26-" + id);
        o.setEmail("autor@x.com"); o.setFullname("Autor");
        o.setType(Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA);
        return o;
    }

    static OccurrenceHistoryDto hist(String kind, String oldS, String newS, LocalDateTime at, Integer dep) {
        OccurrenceHistoryDto h = new OccurrenceHistoryDto();
        h.setKind(kind); h.setOldStatus(oldS); h.setNewStatus(newS); h.setChangedAt(at);
        h.setDepartmentId(dep); h.setChangedByName("Funcionária");
        return h;
    }

    @BeforeEach
    void setUp() {
        sut = new DepartmentAccessRestController(tokens, occurrenceService, departmentService,
                                                 mediaUploadService, userService, emailService);
        request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://app");
        raiz = occ(10, Occurrence.OccurrenceStatus.EM_ANDAMENTO);
        agrupada = occ(11, Occurrence.OccurrenceStatus.PENDENTE);
        deFora = occ(99, Occurrence.OccurrenceStatus.PENDENTE);
        when(tokens.verify("TOKEN")).thenReturn(Optional.of(new Access(10, 10, 3, 5, "https://real")));
        when(occurrenceService.getGroup(10)).thenReturn(List.of(raiz, agrupada));
        when(occurrenceService.findById(10)).thenReturn(raiz);
        when(occurrenceService.findById(11)).thenReturn(agrupada);
        when(occurrenceService.findById(99)).thenReturn(deFora);
        when(departmentService.findById(3)).thenReturn(new Department(3, "Obras", "obras@pref.br", "Santa Rita do Sapucaí", "MG"));
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(occurrenceService.getHistory(10)).thenReturn(List.of(
            hist("STATUS", "PENDENTE", "EM_ANDAMENTO", t, 3)));
    }

    @Test
    @DisplayName("token inválido: 401")
    void invalidToken() {
        when(tokens.verify("RUIM")).thenReturn(Optional.empty());
        assertThat(sut.view("RUIM", null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("ocorrência fora do grupo do link: 404")
    void outsideGroup() {
        assertThat(sut.view("TOKEN", 99).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("sem dados pessoais: autor e nomes do histórico apagados; solicitações escondidas")
    void masksPeople() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(occurrenceService.getHistory(10)).thenReturn(List.of(
            hist("COMPLETION_REQUEST", "EM_ANDAMENTO", "EM_ANDAMENTO", t.plusDays(1), 3),
            hist("STATUS", "PENDENTE", "EM_ANDAMENTO", t, 3)));

        var body = (DepartmentOccurrenceViewDto) sut.view("TOKEN", null).getBody();

        assertThat(body.getOccurrence().getEmail()).isNull();
        assertThat(body.getOccurrence().getFullname()).isNull();
        assertThat(body.getGroup()).extracting(GetOccurrenceDto::getEmail).containsOnlyNulls();
        assertThat(body.getHistory()).hasSize(1);
        assertThat(body.getHistory().get(0).getChangedByName()).isNull();
        assertThat(body.getPendingRequestAt()).isEqualTo(t.plusDays(1));
        assertThat(body.isCanRequestCompletion()).isFalse();
        assertThat(body.getDepartmentName()).isEqualTo("Obras");
    }

    @Test
    @DisplayName("reaberta depois da solicitação: não fica pendente, pode pedir de novo")
    void reopenedClearsPending() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(occurrenceService.getHistory(10)).thenReturn(List.of(
            hist("STATUS", "CONCLUIDA", "EM_ANDAMENTO", t.plusDays(3), null),
            hist("STATUS", "EM_ANDAMENTO", "CONCLUIDA", t.plusDays(2), null),
            hist("COMPLETION_REQUEST", "EM_ANDAMENTO", "EM_ANDAMENTO", t.plusDays(1), 3)));

        var body = (DepartmentOccurrenceViewDto) sut.view("TOKEN", null).getBody();

        assertThat(body.getPendingRequestAt()).isNull();
        assertThat(body.isCanRequestCompletion()).isTrue();
    }

    @Test
    @DisplayName("sem id, abre a ocorrência encaminhada (não a raiz do grupo)")
    void opensForwardedOccurrence() {
        when(tokens.verify("FILHA")).thenReturn(Optional.of(new Access(10, 11, 3, 5, "https://real")));
        when(occurrenceService.getHistory(11)).thenReturn(List.of());

        var body = (DepartmentOccurrenceViewDto) sut.view("FILHA", null).getBody();

        assertThat(body.getOccurrence().getId()).isEqualTo(11);
    }

    @Test
    @DisplayName("reencaminhar ao mesmo departamento libera nova solicitação (refazer o serviço)")
    void reforwardClearsPending() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(occurrenceService.getHistory(10)).thenReturn(List.of(
            hist("STATUS", "EM_ANDAMENTO", "EM_ANDAMENTO", t.plusDays(2), 3),
            hist("COMPLETION_REQUEST", "EM_ANDAMENTO", "EM_ANDAMENTO", t.plusDays(1), 3),
            hist("STATUS", "PENDENTE", "EM_ANDAMENTO", t, 3)));

        var body = (DepartmentOccurrenceViewDto) sut.view("TOKEN", null).getBody();

        assertThat(body.getPendingRequestAt()).isNull();
        assertThat(body.isCanRequestCompletion()).isTrue();
    }

    @Test
    @DisplayName("grupo inteiro encerrado: 410")
    void allClosed() {
        raiz.setStatus(Occurrence.OccurrenceStatus.CONCLUIDA);
        agrupada.setStatus(Occurrence.OccurrenceStatus.INDEFERIDA);
        assertThat(sut.view("TOKEN", null).getStatusCode().value()).isEqualTo(410);
    }

    @Test
    @DisplayName("solicitar: sobe a foto, grava no histórico e avisa quem encaminhou")
    void requestsCompletion() {
        var file = new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(mediaUploadService.uploadSync(any(), any()))
            .thenReturn(new MediaUploadService.UploadResult("pid", "https://img/x.jpg", false, false, null));
        UserModel carlos = new UserModel(); carlos.setEmail("carlos@pref.br");
        when(userService.findById(5)).thenReturn(carlos);

        var resp = sut.requestCompletion("TOKEN", 10, "Buraco tapado", file, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        verify(occurrenceService).requestCompletion(10, 3, "Buraco tapado", "https://img/x.jpg");
        // O link do aviso vem do token (emitido no encaminhamento autenticado), nunca do Origin desta requisição.
        verify(emailService).sendCompletionRequestEmail("carlos@pref.br", "FC-26-10", "Obras",
                                                        "https://real/occurrence/detail/10");
    }

    @Test
    @DisplayName("solicitar: sem imagem 400; foto recusada 422; nada gravado")
    void rejectsBadFiles() {
        var texto = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});
        assertThat(sut.requestCompletion("TOKEN", 10, null, texto, request).getStatusCode().value()).isEqualTo(400);

        var foto = new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1});
        when(mediaUploadService.uploadSync(any(), any()))
            .thenReturn(new MediaUploadService.UploadResult(null, null, true, true, "Imagem imprópria"));
        assertThat(sut.requestCompletion("TOKEN", 10, null, foto, request).getStatusCode().value()).isEqualTo(422);

        verify(occurrenceService, never()).requestCompletion(anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("solicitar: fechada ou com solicitação pendente, 409")
    void conflicts() {
        var foto = new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1});
        raiz.setStatus(Occurrence.OccurrenceStatus.CONCLUIDA);
        assertThat(sut.requestCompletion("TOKEN", 10, null, foto, request).getStatusCode().value()).isEqualTo(409);

        raiz.setStatus(Occurrence.OccurrenceStatus.EM_ANDAMENTO);
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(occurrenceService.getHistory(10)).thenReturn(List.of(
            hist("COMPLETION_REQUEST", "EM_ANDAMENTO", "EM_ANDAMENTO", t.plusDays(1), 3),
            hist("STATUS", "PENDENTE", "EM_ANDAMENTO", t, 3)));
        assertThat(sut.requestCompletion("TOKEN", 10, null, foto, request).getStatusCode().value()).isEqualTo(409);

        verify(mediaUploadService, never()).uploadSync(any(), any());
    }
}
