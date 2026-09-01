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

    OccurrenceServiceImpl sut;

    @BeforeEach
    void setUp() {
        sut = new OccurrenceServiceImpl(occurrenceDao, supportDao, trackingCodeService, emailService);
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
            when(occurrenceDao.countTodayAnonymousByIp("1.2.3.4")).thenReturn(3);

            assertThatThrownBy(() -> sut.createOccurrence(anonymousOccurrence(), "1.2.3.4"))
                .isInstanceOf(IllegalStateException.class);

            verify(occurrenceDao, never()).add(any());
        }

        @Test
        @DisplayName("RF08: grava o IP na ocorrência anônima dentro do limite")
        void storesIpForAnonymousWithinLimit() {
            when(occurrenceDao.countTodayAnonymousByIp("9.9.9.9")).thenReturn(0);
            when(trackingCodeService.generateCode()).thenReturn("CODE1234");
            when(trackingCodeService.hash(any())).thenReturn("hash");
            when(occurrenceDao.add(any())).thenReturn(1);

            sut.createOccurrence(anonymousOccurrence(), "9.9.9.9");

            ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(captor.capture());
            assertThat(captor.getValue().getIpAddress()).isEqualTo("9.9.9.9");
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
}
