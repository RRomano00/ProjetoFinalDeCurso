package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.Occurrence;
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
@DisplayName("OccurrenceServiceImpl – comportamentos gerais")
class OccurrenceServiceImplTest {

    @Mock OccurrenceDao occurrenceDao;
    @Mock br.com.faitec.falacidade.port.dao.occurrence.OccurrenceSupportDao supportDao;
    @Mock AnonymousTrackingCodeService trackingCodeService;
    @Mock br.com.faitec.falacidade.port.service.email.EmailService emailService;

    OccurrenceServiceImpl sut;

    @BeforeEach
    void setUp() {
        sut = new OccurrenceServiceImpl(occurrenceDao, supportDao, trackingCodeService, emailService);
    }

    private Occurrence validAnonymous() {
        Occurrence o = new Occurrence();
        o.setDescription("Buraco enorme");
        o.setCity("Franca");
        o.setType(Occurrence.OccurrenceType.BURACO_NA_RUA_OU_CALCADA);
        o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
        o.setAnonymous(true);
        return o;
    }

    private GetOccurrenceDto dtoWith(int id, String protocol) {
        GetOccurrenceDto g = new GetOccurrenceDto();
        g.setId(id);
        g.setProtocolNumber(protocol);
        return g;
    }

    // ================================================================
    // Prioridade automática (RN04) via createOccurrence()
    // ================================================================

    @Nested
    @DisplayName("Prioridade automática (RN04)")
    class Priority {

        @Test
        @DisplayName("prioridade ALTA atribuída para MAUS_TRATOS_AOS_ANIMAIS")
        void highPriorityForAnimalAbuse() {
            when(trackingCodeService.generateCode()).thenReturn("AAAAAAAA");
            when(trackingCodeService.hash(any())).thenReturn("hash");
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-X");
                return 1;
            });

            Occurrence o = validAnonymous();
            o.setType(Occurrence.OccurrenceType.MAUS_TRATOS_AOS_ANIMAIS);
            o.setPriority(null);

            sut.createOccurrence(o, null);

            ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(captor.capture());
            assertThat(captor.getValue().getPriority()).isEqualTo(Occurrence.Priority.ALTA);
        }

        @Test
        @DisplayName("prioridade existente não é sobrescrita")
        void existingPriorityNotOverwritten() {
            when(trackingCodeService.generateCode()).thenReturn("AAAAAAAA");
            when(trackingCodeService.hash(any())).thenReturn("hash");
            when(occurrenceDao.add(any())).thenAnswer(inv -> {
                ((Occurrence) inv.getArgument(0)).setProtocolNumber("FC-X");
                return 1;
            });

            Occurrence o = validAnonymous();
            o.setPriority(Occurrence.Priority.BAIXA);

            sut.createOccurrence(o, null);

            ArgumentCaptor<Occurrence> captor = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(captor.capture());
            assertThat(captor.getValue().getPriority()).isEqualTo(Occurrence.Priority.BAIXA);
        }
    }

    // ================================================================
    // updateOccurrenceStatusToInProgress()
    // ================================================================

    @Nested
    @DisplayName("updateOccurrenceStatusToInProgress()")
    class ToInProgress {
        @Test @DisplayName("delega ao DAO com id válido")
        void delegates() {
            doNothing().when(occurrenceDao).updateOccurrenceStatusToInProgress(3);
            sut.updateOccurrenceStatusToInProgress(3);
            verify(occurrenceDao).updateOccurrenceStatusToInProgress(3);
        }

        @Test @DisplayName("id negativo é ignorado")
        void ignoresNegative() {
            sut.updateOccurrenceStatusToInProgress(-1);
            verifyNoInteractions(occurrenceDao);
        }
    }

    // ================================================================
    // updateOccurrenceStatusToConclude()
    // ================================================================

    @Nested
    @DisplayName("updateOccurrenceStatusToConclude()")
    class ToConclude {
        @Test @DisplayName("delega ao DAO com id válido")
        void delegates() {
            doNothing().when(occurrenceDao).updateOccurrenceStatusToConclude(5);
            sut.updateOccurrenceStatusToConclude(5);
            verify(occurrenceDao).updateOccurrenceStatusToConclude(5);
        }

        @Test @DisplayName("id negativo é ignorado")
        void ignoresNegative() {
            sut.updateOccurrenceStatusToConclude(-10);
            verifyNoInteractions(occurrenceDao);
        }
    }

    // ================================================================
    // updateStatus()
    // ================================================================

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {
        @Test @DisplayName("delega com todos os parâmetros")
        void delegates() {
            sut.updateStatus(1, "EM_ANDAMENTO", 99, "obs");
            verify(occurrenceDao).updateStatus(1, "EM_ANDAMENTO", 99, "obs");
        }

        @Test @DisplayName("id negativo é ignorado")
        void ignoresNegativeId() {
            sut.updateStatus(-1, "ATENDIDA", 1, "obs");
            verifyNoInteractions(occurrenceDao);
        }

        @Test @DisplayName("status null é ignorado")
        void ignoresNullStatus() {
            sut.updateStatus(1, null, 1, "obs");
            verifyNoInteractions(occurrenceDao);
        }

        @Test @DisplayName("status em branco é ignorado")
        void ignoresBlankStatus() {
            sut.updateStatus(1, "  ", 1, "obs");
            verifyNoInteractions(occurrenceDao);
        }
    }

    // ================================================================
    // findById()
    // ================================================================

    @Nested
    @DisplayName("findById()")
    class FindById {
        @Test @DisplayName("retorna DTO quando encontrado")
        void found() {
            GetOccurrenceDto dto = dtoWith(2, "FC-X");
            when(occurrenceDao.readById(2)).thenReturn(dto);
            assertThat(sut.findById(2)).isSameAs(dto);
        }

        @Test @DisplayName("id negativo retorna null")
        void negativeId() {
            assertThat(sut.findById(-3)).isNull();
            verifyNoInteractions(occurrenceDao);
        }
    }

    // ================================================================
    // findAll()
    // ================================================================

    @Nested
    @DisplayName("findAll()")
    class FindAll {
        @Test @DisplayName("retorna lista do DAO")
        void returnsList() {
            List<GetOccurrenceDto> list = List.of(dtoWith(1, "FC-A"), dtoWith(2, "FC-B"));
            when(occurrenceDao.readall()).thenReturn(list);
            assertThat(sut.findAll()).hasSize(2).isSameAs(list);
        }
    }

    // ================================================================
    // findByProtocolNumber()
    // ================================================================

    @Nested
    @DisplayName("findByProtocolNumber()")
    class FindByProtocol {
        @Test @DisplayName("retorna ocorrência quando encontrada")
        void found() {
            GetOccurrenceDto dto = dtoWith(1, "FC-20260607-AAAAA");
            when(occurrenceDao.readByProtocolNumber("FC-20260607-AAAAA")).thenReturn(dto);
            assertThat(sut.findByProtocolNumber("FC-20260607-AAAAA")).isSameAs(dto);
        }

        @Test @DisplayName("protocolo null retorna null")
        void nullProtocol() {
            assertThat(sut.findByProtocolNumber(null)).isNull();
            verifyNoInteractions(occurrenceDao);
        }
    }

    // ================================================================
    // findNearbyDuplicates()
    // ================================================================

    @Nested
    @DisplayName("findNearbyDuplicates()")
    class FindNearby {
        @Test @DisplayName("passa raio de 50m ao DAO")
        void passesCorrectRadius() {
            when(occurrenceDao.findNearby(anyDouble(), anyDouble(), anyString(), anyDouble()))
                .thenReturn(List.of());
            sut.findNearbyDuplicates(0.0, 0.0, Occurrence.OccurrenceType.LIXO_ACUMULADO_OU_TERRENO_SUJO);
            verify(occurrenceDao).findNearby(0.0, 0.0, "LIXO_ACUMULADO_OU_TERRENO_SUJO", 50.0);
        }

        @Test @DisplayName("type null retorna lista vazia sem chamar DAO")
        void nullTypeReturnsEmpty() {
            assertThat(sut.findNearbyDuplicates(-20.0, -47.0, null)).isEmpty();
            verifyNoInteractions(occurrenceDao);
        }
    }

    // ================================================================
    // findAllByUserEmail() / findAllByCity()  (RF a.8 / b.2)
    // ================================================================

    @Nested
    @DisplayName("findAllByUserEmail() / findAllByCity()")
    class ScopedQueries {

        @Test @DisplayName("findAllByUserEmail delega ao DAO (cidadão vê só as próprias)")
        void byUserEmailDelegates() {
            List<GetOccurrenceDto> list = List.of(dtoWith(1, "FC-A"));
            when(occurrenceDao.readAllByUserEmail("joao@email.com")).thenReturn(list);
            assertThat(sut.findAllByUserEmail("joao@email.com")).isSameAs(list);
        }

        @Test @DisplayName("findAllByUserEmail com email em branco retorna vazio sem chamar DAO")
        void byUserEmailBlank() {
            assertThat(sut.findAllByUserEmail("  ")).isEmpty();
            verifyNoInteractions(occurrenceDao);
        }

        @Test @DisplayName("findAllByCity delega ao DAO (funcionário vê só o município)")
        void byCityDelegates() {
            List<GetOccurrenceDto> list = List.of(dtoWith(1, "FC-A"), dtoWith(2, "FC-B"));
            when(occurrenceDao.readAllByCity("Santa Rita do Sapucaí")).thenReturn(list);
            assertThat(sut.findAllByCity("Santa Rita do Sapucaí")).isSameAs(list);
        }

        @Test @DisplayName("findAllByCity com cidade nula retorna vazio sem chamar DAO")
        void byCityBlank() {
            assertThat(sut.findAllByCity(null)).isEmpty();
            verifyNoInteractions(occurrenceDao);
        }
    }
}
