package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.UserRestController;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.user.UpdateUserDto;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.password.PasswordResetService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Município da equipe e aviso de alteração de conta")
class UserMunicipalityLockTest {

    @Mock UserService          userService;
    @Mock PasswordResetService passwordResetService;
    @Mock EmailService         emailService;
    @Mock MfaService           mfaService;
    @Mock EmailMfaCodeStore    emailMfaCodeStore;

    UserRestController sut;

    @BeforeEach
    void setUp() {
        sut = new UserRestController(userService, passwordResetService, emailService,
                                     mfaService, emailMfaCodeStore);
    }

    private Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null,
            List.of(new SimpleGrantedAuthority("ROLE_TESTE")));
    }

    private UserModel user(int id, String email, UserModel.UserRole role, String city, String state) {
        UserModel u = new UserModel();
        u.setId(id); u.setEmail(email); u.setRole(role);
        u.setCity(city); u.setState(state); u.setFullname("Fulano");
        return u;
    }

    private UpdateUserDto dto(int id, String city, String state) {
        UpdateUserDto d = new UpdateUserDto();
        d.setId(id); d.setFullname("Fulano"); d.setCity(city); d.setState(state);
        return d;
    }

    @Test
    @DisplayName("funcionário não muda o próprio município")
    void employeeCannotMoveItself() {
        UserModel eu = user(5, "func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail("func@prefeitura.com")).thenReturn(eu);

        ResponseEntity<?> res = sut.update(5, dto(5, "Pouso Alegre", "MG"), auth(eu.getEmail()));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userService, never()).update(anyInt(), any());
    }

    @Test
    @DisplayName("administrador municipal não muda o município do seu funcionário")
    void municipalAdminCannotMoveStaff() {
        UserModel adm  = user(1, "adm@prefeitura.com", UserModel.UserRole.ADMINISTRATOR, "Itajubá", "MG");
        UserModel alvo = user(5, "func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail(adm.getEmail())).thenReturn(adm);
        when(userService.findById(5)).thenReturn(alvo);

        ResponseEntity<?> res = sut.update(5, dto(5, "Pouso Alegre", "MG"), auth(adm.getEmail()));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userService, never()).update(anyInt(), any());
    }

    @Test
    @DisplayName("super administrador muda o município da conta da equipe")
    void superAdminMovesStaff() {
        UserModel sa   = user(1, "admin@falacidade.com", UserModel.UserRole.SUPER_ADMIN, null, null);
        UserModel alvo = user(5, "func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail(sa.getEmail())).thenReturn(sa);
        when(userService.findById(5)).thenReturn(alvo);

        ResponseEntity<?> res = sut.update(5, dto(5, "Pouso Alegre", "MG"), auth(sa.getEmail()));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ArgumentCaptor<UserModel> gravado = ArgumentCaptor.forClass(UserModel.class);
        verify(userService).update(eq(5), gravado.capture());
        assertThat(gravado.getValue().getCity()).isEqualTo("Pouso Alegre");
    }

    @Test
    @DisplayName("funcionário edita os próprios dados; o município fica como está")
    void employeeEditsOwnDataKeepingCity() {
        UserModel eu = user(5, "func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail(eu.getEmail())).thenReturn(eu);

        UpdateUserDto d = dto(5, null, null);
        d.setPhoneNumber("(35) 99999-0000");
        ResponseEntity<?> res = sut.update(5, d, auth(eu.getEmail()));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ArgumentCaptor<UserModel> gravado = ArgumentCaptor.forClass(UserModel.class);
        verify(userService).update(eq(5), gravado.capture());
        assertThat(gravado.getValue().getCity()).isEqualTo("Itajubá");
        assertThat(gravado.getValue().getState()).isEqualTo("MG");
    }

    @Test
    @DisplayName("cidadão continua trocando o próprio município — para ele é endereço")
    void citizenStillChangesOwnCity() {
        UserModel eu = user(9, "joao@email.com", UserModel.UserRole.CITIZEN, "Itajubá", "MG");
        when(userService.findByEmail(eu.getEmail())).thenReturn(eu);

        ResponseEntity<?> res = sut.update(9, dto(9, "Pouso Alegre", "MG"), auth(eu.getEmail()));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ArgumentCaptor<UserModel> gravado = ArgumentCaptor.forClass(UserModel.class);
        verify(userService).update(eq(9), gravado.capture());
        assertThat(gravado.getValue().getCity()).isEqualTo("Pouso Alegre");
    }

    @Test
    @DisplayName("alteração feita por outra pessoa avisa o titular, dizendo o que mudou e quem mudou")
    void notifiesTheAccountOwner() {
        UserModel sa   = user(1, "admin@falacidade.com", UserModel.UserRole.SUPER_ADMIN, null, null);
        sa.setFullname("Administrador do Sistema");
        UserModel alvo = user(5, "func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail(sa.getEmail())).thenReturn(sa);
        when(userService.findById(5)).thenReturn(alvo);

        sut.update(5, dto(5, "Pouso Alegre", "MG"), auth(sa.getEmail()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> mudancas = ArgumentCaptor.forClass(List.class);
        verify(emailService).sendAccountChangedEmail(eq("func@prefeitura.com"), eq("Fulano"),
            mudancas.capture(), eq("Administrador do Sistema"), eq("admin@falacidade.com"));
        assertThat(mudancas.getValue()).anyMatch(m -> m.startsWith("Município:")
                                                   && m.contains("Itajubá/MG")
                                                   && m.contains("Pouso Alegre/MG"));
    }

    @Test
    @DisplayName("alteração feita pelo próprio titular não gera aviso")
    void selfServiceSendsNoEmail() {
        UserModel eu = user(9, "joao@email.com", UserModel.UserRole.CITIZEN, "Itajubá", "MG");
        when(userService.findByEmail(eu.getEmail())).thenReturn(eu);

        sut.update(9, dto(9, "Pouso Alegre", "MG"), auth(eu.getEmail()));

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("inativação avisa o titular")
    void deactivationNotifies() {
        UserModel adm  = user(1, "adm@prefeitura.com", UserModel.UserRole.ADMINISTRATOR, "Itajubá", "MG");
        UserModel alvo = user(5, "func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail(adm.getEmail())).thenReturn(adm);
        when(userService.findById(5)).thenReturn(alvo);

        sut.setActive(5, java.util.Map.of("active", false), auth(adm.getEmail()));

        verify(emailService).sendAccountChangedEmail(eq("func@prefeitura.com"), anyString(),
            eq(List.of("Conta inativada")), anyString(), eq("adm@prefeitura.com"));
    }

    @Test
    @DisplayName("troca de perfil avisa o titular, com o perfil antigo e o novo")
    void roleChangeNotifies() {
        UserModel adm  = user(1, "adm@prefeitura.com", UserModel.UserRole.ADMINISTRATOR, "Itajubá", "MG");
        UserModel alvo = user(5, "func@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG");
        when(userService.findByEmail(adm.getEmail())).thenReturn(adm);
        when(userService.findById(5)).thenReturn(alvo);

        sut.setRole(5, java.util.Map.of("role", "ADMINISTRATOR"), auth(adm.getEmail()));

        verify(emailService).sendAccountChangedEmail(eq("func@prefeitura.com"), anyString(),
            eq(List.of("Perfil: Funcionário → Administrador")), anyString(), eq("adm@prefeitura.com"));
    }
}
