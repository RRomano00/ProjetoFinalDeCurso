package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.CustomUserDetailsService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService")
class CustomUserDetailsServiceTest {

    @Mock UserService userService;

    @InjectMocks
    CustomUserDetailsService sut;

    private UserModel userWith(String email, String password, UserModel.UserRole role) {
        UserModel u = new UserModel();
        u.setId(1);
        u.setEmail(email);
        u.setPassword(password);
        u.setRole(role);
        u.setFullname("Teste");
        return u;
    }

    @Nested
    @DisplayName("loadUserByUsername()")
    class LoadUser {

        @Test
        @DisplayName("retorna UserDetails com e-mail como username")
        void returnsCorrectUsername() {
            when(userService.findByEmail("joao@email.com"))
                .thenReturn(userWith("joao@email.com", "$2a$HASH", UserModel.UserRole.CITIZEN));

            UserDetails details = sut.loadUserByUsername("joao@email.com");

            assertThat(details.getUsername()).isEqualTo("joao@email.com");
        }

        @Test
        @DisplayName("senha do UserDetails bate com a do banco")
        void returnsCorrectPassword() {
            when(userService.findByEmail("joao@email.com"))
                .thenReturn(userWith("joao@email.com", "$2a$HASH", UserModel.UserRole.CITIZEN));

            UserDetails details = sut.loadUserByUsername("joao@email.com");

            assertThat(details.getPassword()).isEqualTo("$2a$HASH");
        }

        @Test
        @DisplayName("authority é ROLE_CITIZEN para role CITIZEN (sem espaço – bugfix)")
        void authorityHasCorrectPrefix_citizen() {
            when(userService.findByEmail("joao@email.com"))
                .thenReturn(userWith("joao@email.com", "$2a$HASH", UserModel.UserRole.CITIZEN));

            UserDetails details = sut.loadUserByUsername("joao@email.com");

            assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_CITIZEN");
        }

        @Test
        @DisplayName("authority é ROLE_ADMINISTRATOR para role ADMINISTRATOR")
        void authorityHasCorrectPrefix_admin() {
            when(userService.findByEmail("admin@email.com"))
                .thenReturn(userWith("admin@email.com", "$2a$HASH", UserModel.UserRole.ADMINISTRATOR));

            UserDetails details = sut.loadUserByUsername("admin@email.com");

            assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMINISTRATOR");
        }

        @Test
        @DisplayName("authority é ROLE_EMPLOYEE para role EMPLOYEE")
        void authorityHasCorrectPrefix_employee() {
            when(userService.findByEmail("func@email.com"))
                .thenReturn(userWith("func@email.com", "$2a$HASH", UserModel.UserRole.EMPLOYEE));

            UserDetails details = sut.loadUserByUsername("func@email.com");

            assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_EMPLOYEE");
        }

        @Test
        @DisplayName("authority NÃO contém espaço (bug original era 'Role_ CITIZEN')")
        void authorityHasNoSpaceAfterPrefix() {
            when(userService.findByEmail("joao@email.com"))
                .thenReturn(userWith("joao@email.com", "$2a$HASH", UserModel.UserRole.CITIZEN));

            UserDetails details = sut.loadUserByUsername("joao@email.com");

            details.getAuthorities().forEach(a ->
                assertThat(a.getAuthority()).doesNotContain(" ")
            );
        }

        @Test
        @DisplayName("e-mail inexistente lança UsernameNotFoundException")
        void throwsForUnknownEmail() {
            when(userService.findByEmail("nao@existe.com")).thenReturn(null);

            assertThatThrownBy(() -> sut.loadUserByUsername("nao@existe.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nao@existe.com");
        }

        @Test
        @DisplayName("possui exatamente UMA authority por usuário")
        void hasExactlyOneAuthority() {
            when(userService.findByEmail("joao@email.com"))
                .thenReturn(userWith("joao@email.com", "$2a$HASH", UserModel.UserRole.CITIZEN));

            UserDetails details = sut.loadUserByUsername("joao@email.com");

            assertThat(details.getAuthorities()).hasSize(1);
        }
    }
}
