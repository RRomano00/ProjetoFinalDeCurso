package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.UserRestController;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.user.CreateEmployeeDto;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.password.PasswordResetService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * RF25: o Super Administrador administra todas as contas e nomeia os
 * administradores municipais; o administrador municipal só alcança as contas do
 * seu próprio município, e não promove ninguém a administrador.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Alcance da administração de contas")
class UserAdministrationScopeTest {

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
        // O controlador monta a URI do recurso criado a partir da requisição corrente.
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() { RequestContextHolder.resetRequestAttributes(); }

    private Authentication auth(String email) {
        return new UsernamePasswordAuthenticationToken(email, null,
            List.of(new SimpleGrantedAuthority("ROLE_TESTE")));
    }

    private UserModel user(int id, String email, UserModel.UserRole role, String city, String state) {
        UserModel u = new UserModel();
        u.setId(id); u.setEmail(email); u.setRole(role); u.setCity(city); u.setState(state);
        return u;
    }

    private UserModel superAdmin() {
        return user(1, "admin@falacidade.com", UserModel.UserRole.SUPER_ADMIN, null, null);
    }

    private UserModel adminSantaRita() {
        return user(2, "admin.sr@falacidade.com", UserModel.UserRole.ADMINISTRATOR,
                    "Santa Rita do Sapucaí", "MG");
    }

    private CreateEmployeeDto dto(UserModel.UserRole role, String city, String state) {
        CreateEmployeeDto d = new CreateEmployeeDto();
        d.setFullname("Fulano de Tal"); d.setEmail("fulano@prefeitura.com");
        d.setPassword("Senha@123"); d.setRole(role); d.setCity(city); d.setState(state);
        return d;
    }

    @Nested
    @DisplayName("Criação de contas da administração")
    class Criacao {

        @Test
        @DisplayName("super administrador nomeia administrador e define o município")
        void superAdminCreatesMunicipalAdmin() {
            when(userService.findByEmail("admin@falacidade.com")).thenReturn(superAdmin());
            when(userService.create(any())).thenReturn(9);

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.ADMINISTRATOR, "Itajubá", "MG"), auth("admin@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(userService).create(argThat(u ->
                u.getRole() == UserModel.UserRole.ADMINISTRATOR && "Itajubá".equals(u.getCity())));
        }

        @Test
        @DisplayName("administrador municipal nomeia administrador do seu município")
        void municipalAdminCreatesAdminInItsCity() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.create(any())).thenReturn(12);

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.ADMINISTRATOR, "Santa Rita do Sapucaí", "MG"),
                auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(userService).create(argThat(u -> u.getRole() == UserModel.UserRole.ADMINISTRATOR));
        }

        @Test
        @DisplayName("administrador municipal não nomeia Super Administrador")
        void municipalAdminCannotCreateSuperAdmin() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.SUPER_ADMIN, "Santa Rita do Sapucaí", "MG"),
                auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).create(any());
        }

        @Test
        @DisplayName("administrador municipal não nomeia administrador de outro município")
        void municipalAdminCannotCreateAdminOutsideItsCity() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.ADMINISTRATOR, "Itajubá", "MG"), auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).create(any());
        }

        @Test
        @DisplayName("administrador municipal não cadastra funcionário de outro município")
        void municipalAdminCannotCreateOutsideItsCity() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.EMPLOYEE, "Itajubá", "MG"), auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).create(any());
        }

        @Test
        @DisplayName("administrador municipal cadastra funcionário do seu município")
        void municipalAdminCreatesEmployeeInItsCity() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.create(any())).thenReturn(10);

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.EMPLOYEE, "santa rita do sapucaí", "mg"),
                auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("conta municipal sem município é recusada")
        void municipalityIsRequiredForStaff() {
            when(userService.findByEmail("admin@falacidade.com")).thenReturn(superAdmin());

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.EMPLOYEE, "  ", "MG"), auth("admin@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            verify(userService, never()).create(any());
        }

        @Test
        @DisplayName("super administrador é criado sem município")
        void superAdminHasNoMunicipality() {
            when(userService.findByEmail("admin@falacidade.com")).thenReturn(superAdmin());
            when(userService.create(any())).thenReturn(11);

            ResponseEntity<?> r = sut.createStaff(
                dto(UserModel.UserRole.SUPER_ADMIN, null, null), auth("admin@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @Nested
    @DisplayName("Listagem e alteração de contas")
    class Alcance {

        private List<UserModel> base() {
            return List.of(
                superAdmin(),
                adminSantaRita(),
                user(3, "carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Santa Rita do Sapucaí", "MG"),
                user(4, "joao@email.com",        UserModel.UserRole.CITIZEN,  "Santa Rita do Sapucaí", null),
                user(5, "ana@itajuba.com",       UserModel.UserRole.EMPLOYEE, "Itajubá", "MG"));
        }

        @Test
        @DisplayName("super administrador vê todas as contas")
        void superAdminSeesEveryone() {
            when(userService.findByEmail("admin@falacidade.com")).thenReturn(superAdmin());
            when(userService.findAllUsers()).thenReturn(base());

            assertThat(sut.getAll(auth("admin@falacidade.com")).getBody()).hasSize(5);
        }

        @Test
        @DisplayName("administrador municipal vê apenas as contas do seu município")
        void municipalAdminSeesItsCityOnly() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.findAllUsers()).thenReturn(base());

            assertThat(sut.getAll(auth("admin.sr@falacidade.com")).getBody())
                .extracting(UserModel::getEmail)
                .containsExactlyInAnyOrder("admin.sr@falacidade.com", "carlos@prefeitura.com",
                                           "joao@email.com");
        }

        @Test
        @DisplayName("administrador municipal não inativa conta de outro município")
        void municipalAdminCannotDeactivateOutsider() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.findById(5))
                .thenReturn(user(5, "ana@itajuba.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG"));

            ResponseEntity<Void> r = sut.setActive(5, Map.of("active", false),
                                                   auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).setActive(anyInt(), anyBoolean());
        }

        @Test
        @DisplayName("administrador municipal não alcança o super administrador")
        void municipalAdminCannotTouchSuperAdmin() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.findById(1)).thenReturn(superAdmin());

            ResponseEntity<Void> r = sut.setActive(1, Map.of("active", false),
                                                   auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).setActive(anyInt(), anyBoolean());
        }

        @Test
        @DisplayName("administrador municipal inativa conta do seu município")
        void municipalAdminDeactivatesItsOwn() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.findById(3)).thenReturn(
                user(3, "carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Santa Rita do Sapucaí", "MG"));

            ResponseEntity<Void> r = sut.setActive(3, Map.of("active", false),
                                                   auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(userService).setActive(3, false);
        }

        @Test
        @DisplayName("administrador municipal promove funcionário do seu município")
        void municipalAdminChangesRoleInItsCity() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.findById(3)).thenReturn(
                user(3, "carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Santa Rita do Sapucaí", "MG"));

            ResponseEntity<?> r = sut.setRole(3, Map.of("role", "ADMINISTRATOR"),
                                              auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(userService).setRole(3, UserModel.UserRole.ADMINISTRATOR);
        }

        @Test
        @DisplayName("administrador municipal não nomeia Super Administrador")
        void municipalAdminCannotPromoteToSuperAdmin() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.findById(3)).thenReturn(
                user(3, "carlos@prefeitura.com", UserModel.UserRole.EMPLOYEE, "Santa Rita do Sapucaí", "MG"));

            ResponseEntity<?> r = sut.setRole(3, Map.of("role", "SUPER_ADMIN"),
                                              auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).setRole(anyInt(), any());
        }

        @Test
        @DisplayName("ninguém altera o próprio perfil")
        void cannotChangeOwnRole() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());

            ResponseEntity<?> r = sut.setRole(2, Map.of("role", "SUPER_ADMIN"),
                                              auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).setRole(anyInt(), any());
        }

        @Test
        @DisplayName("administrador municipal não altera o perfil de outro município")
        void municipalAdminCannotChangeRoleOutsider() {
            when(userService.findByEmail("admin.sr@falacidade.com")).thenReturn(adminSantaRita());
            when(userService.findById(5))
                .thenReturn(user(5, "ana@itajuba.com", UserModel.UserRole.EMPLOYEE, "Itajubá", "MG"));

            ResponseEntity<?> r = sut.setRole(5, Map.of("role", "ADMINISTRATOR"),
                                              auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(userService, never()).setRole(anyInt(), any());
        }

        @Test
        @DisplayName("perfil desconhecido é recusado")
        void rejectsUnknownRole() {
            ResponseEntity<?> r = sut.setRole(3, Map.of("role", "CHEFAO"),
                                              auth("admin.sr@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            verify(userService, never()).setRole(anyInt(), any());
        }

        @Test
        @DisplayName("conta ativa não é excluída — é preciso inativar antes")
        void activeAccountIsNotDeleted() {
            UserModel alvo = user(4, "joao@email.com", UserModel.UserRole.CITIZEN,
                                  "Santa Rita do Sapucaí", null);
            alvo.setActive(true);
            when(userService.findByEmail("admin@falacidade.com")).thenReturn(superAdmin());
            when(userService.findById(4)).thenReturn(alvo);

            ResponseEntity<Void> r = sut.delete(4, auth("admin@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            verify(userService, never()).delete(anyInt());
        }

        @Test
        @DisplayName("super administrador exclui a conta já inativada")
        void superAdminDeletesInactiveAccount() {
            UserModel alvo = user(4, "joao@email.com", UserModel.UserRole.CITIZEN,
                                  "Santa Rita do Sapucaí", null);
            alvo.setActive(false);
            when(userService.findByEmail("admin@falacidade.com")).thenReturn(superAdmin());
            when(userService.findById(4)).thenReturn(alvo);

            ResponseEntity<Void> r = sut.delete(4, auth("admin@falacidade.com"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(userService).delete(4);
        }
    }
}
