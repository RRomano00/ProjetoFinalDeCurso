package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.OccurrenceRestController;
import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.UpdateOccurrenceStatusDto;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.media.MediaUploadService;
import br.com.faitec.falacidade.port.service.occurrence.OccurrenceService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OccurrenceRestController – exclusão (finalização) de ocorrência pelo Super Administrador")
class OccurrenceDeletionTest {

    @Mock OccurrenceService  occurrenceService;
    @Mock MediaUploadService mediaUploadService;
    @Mock UserService        userService;
    @Mock EmailService       emailService;

    OccurrenceRestController sut;
    GetOccurrenceDto occurrence;

    @BeforeEach
    void setUp() {
        sut = new OccurrenceRestController(occurrenceService, mediaUploadService, userService, emailService);
        occurrence = new GetOccurrenceDto();
        occurrence.setId(7); occurrence.setProtocolNumber("FC-26-ABCDE"); occurrence.setTitle("Buraco na rua");
        occurrence.setCity("Santa Rita do Sapucaí"); occurrence.setState("MG");
        occurrence.setStatus(Occurrence.OccurrenceStatus.PENDENTE);
        when(occurrenceService.findById(7)).thenReturn(occurrence);
        when(occurrenceService.finalizeOccurrence(eq(7), anyInt())).thenReturn(List.of("p1", "p2"));
    }

    private Authentication as(UserModel.UserRole role) {
        UserModel u = new UserModel();
        u.setId(1); u.setEmail("quem@falacidade.com"); u.setRole(role);
        when(userService.findByEmail("quem@falacidade.com")).thenReturn(u);
        return new UsernamePasswordAuthenticationToken("quem@falacidade.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    @DisplayName("Super Administrador finaliza: status muda, fotos saem do Cloudinary e a exclusão vai para o log")
    void superAdminFinalizes() {
        assertThat(sut.deleteOccurrence(7, as(UserModel.UserRole.SUPER_ADMIN)).getStatusCode().value()).isEqualTo(204);
        verify(occurrenceService).finalizeOccurrence(7, 1);
        verify(mediaUploadService).delete("p1");
        verify(mediaUploadService).delete("p2");
        verify(userService).logOccurrenceDeletion(any(UserModel.class), eq("FC-26-ABCDE"), eq("Buraco na rua"),
                                                  eq("Santa Rita do Sapucaí"), eq("MG"));
    }

    @ParameterizedTest
    @EnumSource(value = UserModel.UserRole.class, names = {"ADMINISTRATOR", "EMPLOYEE", "CITIZEN"})
    @DisplayName("os outros perfis recebem 403 e nada muda")
    void otherRolesAreForbidden(UserModel.UserRole role) {
        assertThat(sut.deleteOccurrence(7, as(role)).getStatusCode().value()).isEqualTo(403);
        verify(occurrenceService, never()).finalizeOccurrence(anyInt(), anyInt());
        verify(mediaUploadService, never()).delete(anyString());
    }

    @Test
    @DisplayName("sem login, 403")
    void anonymousIsForbidden() {
        assertThat(sut.deleteOccurrence(7, null).getStatusCode().value()).isEqualTo(403);
        verify(occurrenceService, never()).finalizeOccurrence(anyInt(), anyInt());
    }

    @Test
    @DisplayName("inexistente ou já finalizada, 404")
    void unknownOrAlreadyFinalized() {
        Authentication superAdmin = as(UserModel.UserRole.SUPER_ADMIN);
        assertThat(sut.deleteOccurrence(99, superAdmin).getStatusCode().value()).isEqualTo(404);
        occurrence.setStatus(Occurrence.OccurrenceStatus.FINALIZADA);
        assertThat(sut.deleteOccurrence(7, superAdmin).getStatusCode().value()).isEqualTo(404);
        verify(occurrenceService, never()).finalizeOccurrence(anyInt(), anyInt());
    }

    @Test
    @DisplayName("só o Super Administrador vê a finalizada; para os outros ela não existe")
    void finalizedOnlyForSuperAdmin() {
        occurrence.setStatus(Occurrence.OccurrenceStatus.FINALIZADA);
        GetOccurrenceDto aberta = new GetOccurrenceDto();
        aberta.setId(8); aberta.setStatus(Occurrence.OccurrenceStatus.PENDENTE); aberta.setCity("Santa Rita do Sapucaí");
        when(occurrenceService.findAll()).thenReturn(List.of(occurrence, aberta));
        when(occurrenceService.findAllByCity(any())).thenReturn(List.of(occurrence, aberta));
        when(occurrenceService.findAllByUserEmail(anyString())).thenReturn(List.of(occurrence, aberta));
        when(occurrenceService.findByProtocolNumber("FC-26-ABCDE")).thenReturn(occurrence);
        when(occurrenceService.getGroup(8)).thenReturn(List.of(occurrence, aberta));

        Authentication superAdmin = as(UserModel.UserRole.SUPER_ADMIN);
        assertThat(sut.getById(7, superAdmin).getStatusCode().value()).isEqualTo(200);
        assertThat(sut.getAll(superAdmin).getBody()).hasSize(2);

        for (UserModel.UserRole role : List.of(UserModel.UserRole.ADMINISTRATOR, UserModel.UserRole.EMPLOYEE,
                                              UserModel.UserRole.CITIZEN)) {
            Authentication auth = as(role);
            assertThat(sut.getById(7, auth).getStatusCode().value()).as(role.name()).isEqualTo(404);
            assertThat(sut.getByProtocol("FC-26-ABCDE", auth).getStatusCode().value()).as(role.name()).isEqualTo(404);
            assertThat(sut.getAll(auth).getBody()).as(role.name()).extracting(GetOccurrenceDto::getId).containsExactly(8);
            assertThat(sut.mine(auth).getBody()).as(role.name()).extracting(GetOccurrenceDto::getId).containsExactly(8);
            assertThat(sut.getGroup(8, auth).getBody()).as(role.name()).extracting(GetOccurrenceDto::getId).containsExactly(8);
            assertThat(sut.getHistory(7, auth).getStatusCode().value()).as(role.name()).isEqualTo(404);
        }
        assertThat(sut.getById(7, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("finalizada é definitiva: a troca de status é recusada, e Finalizada não vale pela troca comum")
    void finalizedIsFinal() {
        Authentication superAdmin = as(UserModel.UserRole.SUPER_ADMIN);
        UpdateOccurrenceStatusDto paraFinalizada = new UpdateOccurrenceStatusDto();
        paraFinalizada.setNewStatus(Occurrence.OccurrenceStatus.FINALIZADA);
        assertThat(sut.updateStatus(7, paraFinalizada, superAdmin).getStatusCode().value()).isEqualTo(400);

        occurrence.setStatus(Occurrence.OccurrenceStatus.FINALIZADA);
        UpdateOccurrenceStatusDto reabrir = new UpdateOccurrenceStatusDto();
        reabrir.setNewStatus(Occurrence.OccurrenceStatus.PENDENTE);
        assertThat(sut.updateStatus(7, reabrir, superAdmin).getStatusCode().value()).isEqualTo(409);
        assertThat(sut.toInProgress(7, null, superAdmin).getStatusCode().value()).isEqualTo(409);
        assertThat(sut.toConclude(7, null, superAdmin).getStatusCode().value()).isEqualTo(409);
        verify(occurrenceService, never()).changeStatus(anyInt(), anyString(), anyInt(), any(), anyBoolean());
    }
}
