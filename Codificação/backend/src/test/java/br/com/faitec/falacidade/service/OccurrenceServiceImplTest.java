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
    @Mock br.com.faitec.falacidade.port.service.department.DepartmentService departmentService;
    @Mock br.com.faitec.falacidade.implementation.service.department.DepartmentAccessTokenService accessTokens;

    OccurrenceServiceImpl sut;

    @BeforeEach
    void setUp() {
        sut = new OccurrenceServiceImpl(occurrenceDao, supportDao, trackingCodeService, emailService,
                                        departmentService, accessTokens);
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
            sut.updateStatus(-1, "CONCLUIDA", 1, "obs");
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

    @Nested
    @DisplayName("agrupamento automático na criação")
    class AutoGroup {

        @BeforeEach
        void stubs() {
            when(trackingCodeService.generateCode()).thenReturn("AAAAAAAA");
            when(trackingCodeService.hash(any())).thenReturn("hash");
            when(occurrenceDao.add(any())).thenReturn(1);
        }

        private Occurrence locatedAt() {
            Occurrence o = validAnonymous();
            o.setLatitude(-22.25);
            o.setLongitude(-45.70);
            return o;
        }

        private Occurrence created() {
            ArgumentCaptor<Occurrence> c = ArgumentCaptor.forClass(Occurrence.class);
            verify(occurrenceDao).add(c.capture());
            return c.getValue();
        }

        @Test
        @DisplayName("entra no grupo da ocorrência aberta do mesmo tipo a até 50 m, sem perguntar ao usuário")
        void joinsNearbyOccurrence() {
            when(occurrenceDao.findNearby(-22.25, -45.70, "BURACO_NA_RUA_OU_CALCADA", 50.0))
                .thenReturn(List.of(dtoWith(5, "FC-26-AAAAA")));

            var resposta = sut.createOccurrence(locatedAt(), null);

            assertThat(created().getGroupId()).isEqualTo(5);
            assertThat(resposta.getGroupedWithProtocol()).isEqualTo("FC-26-AAAAA");
        }

        @Test
        @DisplayName("se a vizinha já faz parte de um grupo, entra no mesmo grupo")
        void joinsExistingGroup() {
            GetOccurrenceDto vizinha = dtoWith(5, "FC-26-AAAAA");
            vizinha.setGroupId(3);
            when(occurrenceDao.findNearby(-22.25, -45.70, "BURACO_NA_RUA_OU_CALCADA", 50.0))
                .thenReturn(List.of(vizinha));

            sut.createOccurrence(locatedAt(), null);

            assertThat(created().getGroupId()).isEqualTo(3);
        }

        @Test
        @DisplayName("sem vizinha do mesmo tipo, fica sozinha")
        void staysAlone() {
            when(occurrenceDao.findNearby(anyDouble(), anyDouble(), anyString(), anyDouble())).thenReturn(List.of());

            var resposta = sut.createOccurrence(locatedAt(), null);

            assertThat(created().getGroupId()).isNull();
            assertThat(resposta.getGroupedWithProtocol()).isNull();
        }
    }

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

    @Nested
    @DisplayName("getSupportedOccurrenceIds()")
    class SupportedIds {

        @Test @DisplayName("delega ao DAO os ids apoiados pelo cidadão")
        void delegatesToDao() {
            when(supportDao.findOccurrenceIdsByCitizen(7)).thenReturn(List.of(3, 9));
            assertThat(sut.getSupportedOccurrenceIds(7)).containsExactly(3, 9);
        }

        @Test @DisplayName("id de cidadão inválido retorna vazio sem consultar o DAO")
        void guardsInvalidCitizen() {
            assertThat(sut.getSupportedOccurrenceIds(0)).isEmpty();
            verifyNoInteractions(supportDao);
        }
    }

    @Nested
    @DisplayName("unsupportOccurrence()")
    class Unsupport {

        @Test @DisplayName("remove o apoio quando a ocorrência existe")
        void removesSupport() {
            when(occurrenceDao.readById(5)).thenReturn(new GetOccurrenceDto());
            when(supportDao.removeSupport(5, 7)).thenReturn(true);

            assertThat(sut.unsupportOccurrence(5, 7)).isTrue();
            verify(supportDao).removeSupport(5, 7);
        }

        @Test @DisplayName("retorna false quando o cidadão não apoiava")
        void returnsFalseWhenNotSupported() {
            when(occurrenceDao.readById(5)).thenReturn(new GetOccurrenceDto());
            when(supportDao.removeSupport(5, 7)).thenReturn(false);

            assertThat(sut.unsupportOccurrence(5, 7)).isFalse();
        }

        @Test @DisplayName("ocorrência inexistente lança IllegalArgumentException")
        void throwsWhenOccurrenceMissing() {
            when(occurrenceDao.readById(99)).thenReturn(null);

            assertThatThrownBy(() -> sut.unsupportOccurrence(99, 7))
                .isInstanceOf(IllegalArgumentException.class);
            verify(supportDao, never()).removeSupport(anyInt(), anyInt());
        }

        @Test @DisplayName("id de cidadão inválido não chega ao DAO")
        void guardsInvalidCitizen() {
            assertThat(sut.unsupportOccurrence(5, 0)).isFalse();
            verifyNoInteractions(supportDao);
        }
    }

    @Nested
    @DisplayName("ocorrência finalizada")
    class Finalized {

        @Test
        @DisplayName("finalizar não apaga: lê e limpa as fotos, muda o status com histórico e avisa o autor")
        void finalizesWithoutDeleting() {
            GetOccurrenceDto o = dtoWith(7, "FC-26-ABCDE");
            o.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
            o.setEmail("autor@email.com");
            o.setFullname("Ana");
            when(occurrenceDao.readById(7)).thenReturn(o);
            when(occurrenceDao.readMediaPublicIds(7)).thenReturn(List.of("p1", "p2"));

            assertThat(sut.finalizeOccurrence(7, 1)).containsExactly("p1", "p2");

            org.mockito.InOrder ordem = inOrder(occurrenceDao);
            ordem.verify(occurrenceDao).readMediaPublicIds(7);
            ordem.verify(occurrenceDao).clearMedia(7);
            ordem.verify(occurrenceDao).updateStatus(eq(7), eq("FINALIZADA"), eq(1), anyString());
            verify(emailService).sendStatusChangeEmail(eq("autor@email.com"), eq("Ana"), eq("FC-26-ABCDE"),
                                                       eq("FINALIZADA"), anyString());
        }

        @Test
        @DisplayName("a atualização coletiva do grupo pula as ocorrências finalizadas")
        void collectiveSkipsFinalized() {
            GetOccurrenceDto raiz = dtoWith(1, "FC-26-AAAAA");
            raiz.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
            GetOccurrenceDto finalizada = dtoWith(2, "FC-26-BBBBB");
            finalizada.setStatus(Occurrence.OccurrenceStatus.FINALIZADA);
            finalizada.setGroupId(1);
            when(occurrenceDao.readById(1)).thenReturn(raiz);
            when(occurrenceDao.readGroup(1)).thenReturn(List.of(raiz, finalizada));

            sut.changeStatus(1, "CONCLUIDA", 9, "resolvido", true);

            verify(occurrenceDao).updateStatus(1, "CONCLUIDA", 9, "resolvido");
            verify(occurrenceDao, never()).updateStatus(eq(2), anyString(), anyInt(), any());
        }
    }
}
