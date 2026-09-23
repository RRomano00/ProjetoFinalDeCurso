package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceResponseDto;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.implementation.service.occurrence.OccurrenceServiceImpl;
import br.com.faitec.falacidade.implementation.service.tracking.AnonymousTrackingCodeService;
import br.com.faitec.falacidade.port.dao.occurrence.OccurrenceDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.faitec.falacidade.domain.Department;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccurrenceServiceImpl – com tracking anônimo")
class OccurrenceServiceImplWithTrackingTest {

    @Mock OccurrenceDao occurrenceDao;
    @Mock br.com.faitec.falacidade.port.dao.occurrence.OccurrenceSupportDao supportDao;
    @Mock AnonymousTrackingCodeService trackingCodeService;
    @Mock br.com.faitec.falacidade.port.service.email.EmailService emailService;
    @Mock br.com.faitec.falacidade.port.service.department.DepartmentService departmentService;

    OccurrenceServiceImpl sut;

    @BeforeEach
    void setUp() {
        sut = new OccurrenceServiceImpl(occurrenceDao, supportDao, trackingCodeService, emailService,
                                        departmentService);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Occurrence anonymousOccurrence() {
        Occurrence o = new Occurrence();
        o.setDescription("Maus tratos");
        o.setCity("Franca");
        o.setType(Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS);
        o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
        o.setAnonymous(true);
        o.setEmail(null);
        return o;
    }

    private Occurrence identifiedOccurrence() {
        Occurrence o = new Occurrence();
        o.setDescription("Buraco na rua");
        o.setCity("Franca");
        o.setType(Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA);
        o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
        o.setAnonymous(false);
        o.setEmail("joao@email.com");
        return o;
    }

    // ================================================================
    // createOccurrence() – ocorrência ANÔNIMA
    // ================================================================

    @Nested
    @DisplayName("createOccurrence() – anônima")
    class CreateAnonymous {

        @Test
        @DisplayName("gera código, hasheia e persiste o hash no DAO")
        void generatesCodeAndPersistsHash() {
            when(trackingCodeService.generateCode()).thenReturn("A3KP7NB2");
            when(trackingCodeService.hash("A3KP7NB2")).thenReturn("abc123hash64chars");
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                Occurrence o = inv.getArgument(0);
                o.setProtocolNumber("FC-20260607-AAAAA");
                return 1;
            });

            sut.createOccurrence(anonymousOccurrence(), null);

            ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(captor.capture());
            assertThat(captor.getValue().getAnonymousTrackingCodeHash())
                .isEqualTo("abc123hash64chars");
        }

        @Test
        @DisplayName("retorna trackingCode plain text no response (só uma vez)")
        void returnsPlainCodeInResponse() {
            when(trackingCodeService.generateCode()).thenReturn("A3KP7NB2");
            when(trackingCodeService.hash("A3KP7NB2")).thenReturn("somehash");
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-X");
                return 5;
            });

            CreateOccurrenceResponseDto response =
                sut.createOccurrence(anonymousOccurrence(), null);

            assertThat(response.getTrackingCode()).isEqualTo("A3KP7NB2");
            assertThat(response.isAnonymous()).isTrue();
            assertThat(response.getOccurrenceId()).isEqualTo(5);
        }

        @Test
        @DisplayName("protocolNumber no response vem do que a DAO gravou na entidade")
        void responseContainsProtocolFromDao() {
            when(trackingCodeService.generateCode()).thenReturn("XXXXXXXX");
            when(trackingCodeService.hash(any())).thenReturn("hash");
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-20260607-TEST1");
                return 3;
            });

            CreateOccurrenceResponseDto response =
                sut.createOccurrence(anonymousOccurrence(), null);

            assertThat(response.getProtocolNumber()).isEqualTo("FC-20260607-TEST1");
        }

        @Test
        @DisplayName("plain text NÃO é gravado no banco (só o hash)")
        void plainTextNotPersistedInDb() {
            when(trackingCodeService.generateCode()).thenReturn("MYCODE12");
            when(trackingCodeService.hash("MYCODE12")).thenReturn("sha256hash");
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-X");
                return 1;
            });

            sut.createOccurrence(anonymousOccurrence(), null);

            ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(captor.capture());
            // O campo persistido deve ser o hash, nunca o código plain
            assertThat(captor.getValue().getAnonymousTrackingCodeHash())
                .isEqualTo("sha256hash")
                .isNotEqualTo("MYCODE12");
        }
    }

    // ================================================================
    // createOccurrence() – ocorrência IDENTIFICADA
    // ================================================================

    @Nested
    @DisplayName("createOccurrence() – identificada")
    class CreateIdentified {

        @Test
        @DisplayName("NÃO gera código de rastreamento para ocorrência identificada")
        void doesNotGenerateCodeForIdentified() {
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-X");
                return 2;
            });

            sut.createOccurrence(identifiedOccurrence(), null);

            verify(trackingCodeService, never()).generateCode();
            verify(trackingCodeService, never()).hash(anyString());
        }

        @Test
        @DisplayName("hash de rastreamento é null para ocorrência identificada")
        void hashIsNullForIdentified() {
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-X");
                return 2;
            });

            sut.createOccurrence(identifiedOccurrence(), null);

            ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(captor.capture());
            assertThat(captor.getValue().getAnonymousTrackingCodeHash()).isNull();
        }

        @Test
        @DisplayName("trackingCode no response é null para ocorrência identificada")
        void trackingCodeNullInResponseForIdentified() {
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-X");
                return 2;
            });

            CreateOccurrenceResponseDto response =
                sut.createOccurrence(identifiedOccurrence(), null);

            assertThat(response.getTrackingCode()).isNull();
            assertThat(response.isAnonymous()).isFalse();
        }
    }

    // ================================================================
    // createOccurrence() – validações
    // ================================================================

    @Nested
    @DisplayName("createOccurrence() – validações")
    class CreateValidations {

        @Test
        @DisplayName("entity null lança IllegalArgumentException")
        void nullEntityThrows() {
            assertThatThrownBy(() -> sut.createOccurrence(null, null))
                .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(occurrenceDao);
        }

        @Test
        @DisplayName("descrição em branco lança IllegalArgumentException")
        void blankDescriptionThrows() {
            Occurrence o = anonymousOccurrence();
            o.setDescription("  ");
            assertThatThrownBy(() -> sut.createOccurrence(o, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("cidade em branco lança IllegalArgumentException")
        void blankCityThrows() {
            Occurrence o = anonymousOccurrence();
            o.setCity("");
            assertThatThrownBy(() -> sut.createOccurrence(o, null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ================================================================
    // findByAnonymousTrackingCode()
    // ================================================================

    @Nested
    @DisplayName("findByAnonymousTrackingCode()")
    class FindByTrackingCode {

        @Test
        @DisplayName("busca pelo hash do código, não pelo plain text")
        void searchesByHash() {
            when(trackingCodeService.hash("A3KP7NB2")).thenReturn("sha256hash");
            GetOccurrenceDto dto = new GetOccurrenceDto();
            dto.setId(1);
            when(occurrenceDao.findByAnonymousTrackingCodeHash("sha256hash")).thenReturn(dto);

            GetOccurrenceDto result = sut.findByAnonymousTrackingCode("A3KP7NB2");

            assertThat(result).isSameAs(dto);
            verify(occurrenceDao).findByAnonymousTrackingCodeHash("sha256hash");
            // DAO nunca recebe o código plain text
            verify(occurrenceDao, never()).findByAnonymousTrackingCodeHash("A3KP7NB2");
        }

        @Test
        @DisplayName("normaliza para maiúsculo e sem espaços antes de hashear")
        void normalizesBeforeHashing() {
            when(trackingCodeService.hash("A3KP7NB2")).thenReturn("hash");
            when(occurrenceDao.findByAnonymousTrackingCodeHash("hash")).thenReturn(null);

            sut.findByAnonymousTrackingCode("  a3kp7nb2  ");

            verify(trackingCodeService).hash("A3KP7NB2");
        }

        @Test
        @DisplayName("código null retorna null sem chamar DAO")
        void nullCodeReturnsNull() {
            assertThat(sut.findByAnonymousTrackingCode(null)).isNull();
            verifyNoInteractions(occurrenceDao);
            verifyNoInteractions(trackingCodeService);
        }

        @Test
        @DisplayName("código em branco retorna null sem chamar DAO")
        void blankCodeReturnsNull() {
            assertThat(sut.findByAnonymousTrackingCode("   ")).isNull();
            verifyNoInteractions(occurrenceDao);
        }

        @Test
        @DisplayName("código inválido retorna null quando DAO não encontra")
        void unknownCodeReturnsNull() {
            when(trackingCodeService.hash(anyString())).thenReturn("hash");
            when(occurrenceDao.findByAnonymousTrackingCodeHash("hash")).thenReturn(null);

            assertThat(sut.findByAnonymousTrackingCode("XXXXXXXX")).isNull();
        }
    }

    // ================================================================
    // Rate limiting diário (RF07 / RF08)
    // ================================================================

    @Nested
    @DisplayName("Rate limiting (RF07/RF08)")
    class RateLimiting {

        @Test
        @DisplayName("RF08: bloqueia 4ª ocorrência anônima do mesmo IP no dia")
        void blocksAnonymousOverDailyLimit() {
            // RNF17: a contagem diária é feita sobre o resumo SHA-256 do IP
            when(trackingCodeService.hash("1.2.3.4")).thenReturn("ipHash1234");
            when(occurrenceDao.countTodayAnonymousByIp("ipHash1234")).thenReturn(3);

            assertThatThrownBy(() -> sut.createOccurrence(anonymousOccurrence(), "1.2.3.4"))
                .isInstanceOf(IllegalStateException.class);

            verify(occurrenceDao, never()).add(any());
        }

        @Test
        @DisplayName("RNF17: grava o resumo SHA-256 do IP, nunca o IP em texto claro")
        void storesHashedIpForAnonymousWithinLimit() {
            when(trackingCodeService.hash("9.9.9.9")).thenReturn("ipHash9999");
            when(occurrenceDao.countTodayAnonymousByIp("ipHash9999")).thenReturn(0);
            when(trackingCodeService.generateCode()).thenReturn("CODE1234");
            when(trackingCodeService.hash("CODE1234")).thenReturn("hash");
            when(occurrenceDao.add(any())).thenReturn(1);

            sut.createOccurrence(anonymousOccurrence(), "9.9.9.9");

            ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(captor.capture());
            assertThat(captor.getValue().getIpAddress()).isEqualTo("ipHash9999");
            assertThat(captor.getValue().getIpAddress()).isNotEqualTo("9.9.9.9");
        }

        @Test
        @DisplayName("RF07: bloqueia 6ª ocorrência identificada do usuário no dia")
        void blocksIdentifiedOverDailyLimit() {
            when(occurrenceDao.countTodayByEmail("joao@email.com")).thenReturn(5);

            assertThatThrownBy(() -> sut.createOccurrence(identifiedOccurrence(), "1.2.3.4"))
                .isInstanceOf(IllegalStateException.class);

            verify(occurrenceDao, never()).add(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RF22: encaminhamento ao departamento responsável
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("forwardToDepartment()")
    class ForwardToDepartment {

        private br.com.faitec.falacidade.domain.Department departamento() {
            return new br.com.faitec.falacidade.domain.Department(
                3, "Secretaria de Obras", "obras@prefeitura.exemplo.br",
                "Santa Rita do Sapucaí", "MG");
        }

        private GetOccurrenceDto ocorrencia() {
            GetOccurrenceDto o = new GetOccurrenceDto();
            o.setId(10);
            o.setProtocolNumber("FC-20260918-A1B2C");
            o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
            return o;
        }

        @Test
        @DisplayName("envia ao e-mail do departamento e passa a ocorrência para EM_ANDAMENTO")
        void forwardsAndMovesToInProgress() {
            when(occurrenceDao.readById(10)).thenReturn(ocorrencia());
            when(departmentService.findById(3)).thenReturn(departamento());

            var resultado = sut.forwardToDepartment(10, List.of(3), 5);

            assertThat(resultado.enviados()).containsExactly("Secretaria de Obras");
            assertThat(resultado.falharam()).isEmpty();
            verify(emailService).sendOccurrenceForwardEmail(
                eq("obras@prefeitura.exemplo.br"), eq("Secretaria de Obras"), any());
            verify(occurrenceDao).updateStatus(eq(10), eq("EM_ANDAMENTO"), eq(5),
                contains("Ocorrência encaminhada para departamento responsável"), eq(3));
        }

        @Test
        @DisplayName("o histórico nomeia o departamento e guarda o seu identificador")
        void writesDepartmentInHistory() {
            when(occurrenceDao.readById(10)).thenReturn(ocorrencia());
            when(departmentService.findById(3)).thenReturn(departamento());

            sut.forwardToDepartment(10, List.of(3), 5);

            ArgumentCaptor<String> nota = ArgumentCaptor.forClass(String.class);
            verify(occurrenceDao).updateStatus(anyInt(), anyString(), anyInt(), nota.capture(), eq(3));
            assertThat(nota.getValue()).isEqualTo(
                "Ocorrência encaminhada para departamento responsável — Secretaria de Obras.");
        }

        @Test
        @DisplayName("falha no envio do e-mail não registra o encaminhamento daquele destino")
        void keepsOccurrenceUntouchedWhenEmailFails() {
            when(occurrenceDao.readById(10)).thenReturn(ocorrencia());
            when(departmentService.findById(3)).thenReturn(departamento());
            doThrow(new RuntimeException("SMTP fora do ar"))
                .when(emailService).sendOccurrenceForwardEmail(anyString(), anyString(), any());

            var resultado = sut.forwardToDepartment(10, List.of(3), 5);

            assertThat(resultado.enviados()).isEmpty();
            assertThat(resultado.falharam()).containsExactly("Secretaria de Obras");
            verify(occurrenceDao, never()).updateStatus(anyInt(), anyString(), anyInt(), anyString(), any());
        }

        @Test
        @DisplayName("encaminha a vários departamentos: um e-mail e um registro para cada")
        void forwardsToSeveralDepartments() {
            when(occurrenceDao.readById(10)).thenReturn(ocorrencia());
            when(departmentService.findById(3)).thenReturn(departamento());
            Department iluminacao = new Department();
            iluminacao.setId(4);
            iluminacao.setName("Secretaria de Iluminação");
            iluminacao.setEmail("luz@prefeitura.exemplo.br");
            when(departmentService.findById(4)).thenReturn(iluminacao);

            var resultado = sut.forwardToDepartment(10, List.of(3, 4), 5);

            assertThat(resultado.enviados())
                .containsExactly("Secretaria de Obras", "Secretaria de Iluminação");
            verify(emailService).sendOccurrenceForwardEmail(
                eq("obras@prefeitura.exemplo.br"), eq("Secretaria de Obras"), any());
            verify(emailService).sendOccurrenceForwardEmail(
                eq("luz@prefeitura.exemplo.br"), eq("Secretaria de Iluminação"), any());
            verify(occurrenceDao).updateStatus(eq(10), eq("EM_ANDAMENTO"), eq(5), anyString(), eq(3));
            verify(occurrenceDao).updateStatus(eq(10), eq("EM_ANDAMENTO"), eq(5), anyString(), eq(4));
        }

        @Test
        @DisplayName("o mesmo departamento repetido na lista recebe uma vez só")
        void ignoresRepeatedDepartment() {
            when(occurrenceDao.readById(10)).thenReturn(ocorrencia());
            when(departmentService.findById(3)).thenReturn(departamento());

            var resultado = sut.forwardToDepartment(10, List.of(3, 3, 3), 5);

            assertThat(resultado.enviados()).containsExactly("Secretaria de Obras");
            verify(emailService, times(1)).sendOccurrenceForwardEmail(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("recusa ocorrência ou departamento inexistentes, sem enviar e-mail")
        void rejectsUnknownOccurrenceOrDepartment() {
            when(occurrenceDao.readById(99)).thenReturn(null);
            assertThatThrownBy(() -> sut.forwardToDepartment(99, List.of(3), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ocorrência");

            when(occurrenceDao.readById(10)).thenReturn(ocorrencia());
            when(departmentService.findById(77)).thenReturn(null);
            assertThatThrownBy(() -> sut.forwardToDepartment(10, List.of(77), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Departamento");

            verify(emailService, never()).sendOccurrenceForwardEmail(anyString(), anyString(), any());
        }
    }
}
