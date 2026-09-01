package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.authentication.JwtAuthenticationServiceImpl;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationServiceImpl")
class JwtAuthenticationServiceImplTest {

    @Mock UserService     userService;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    JwtAuthenticationServiceImpl sut;

    private UserModel userWith(String email, String encodedPass) {
        UserModel u = new UserModel();
        u.setId(1);
        u.setEmail(email);
        u.setPassword(encodedPass);
        u.setFullname("Teste");
        u.setRole(UserModel.UserRole.CITIZEN);
        return u;
    }

    @Nested
    @DisplayName("authenticate()")
    class Authenticate {

        @Test
        @DisplayName("retorna UserModel quando credenciais estão corretas")
        void success() {
            UserModel user = userWith("joao@email.com", "$2a$HASH");
            when(userService.findByEmail("joao@email.com")).thenReturn(user);
            when(passwordEncoder.matches("Senha@123", "$2a$HASH")).thenReturn(true);

            UserModel result = sut.authenticate("joao@email.com", "Senha@123");

            assertThat(result).isSameAs(user);
        }

        @Test
        @DisplayName("lança UsernameNotFoundException quando e-mail não existe")
        void throwsWhenEmailNotFound() {
            when(userService.findByEmail("nao@existe.com")).thenReturn(null);

            assertThatThrownBy(() -> sut.authenticate("nao@existe.com", "qualquer"))
                .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("lança BadCredentialsException quando senha está errada")
        void throwsWhenWrongPassword() {
            when(userService.findByEmail("joao@email.com"))
                .thenReturn(userWith("joao@email.com", "$2a$HASH"));
            when(passwordEncoder.matches("Errada", "$2a$HASH")).thenReturn(false);

            assertThatThrownBy(() -> sut.authenticate("joao@email.com", "Errada"))
                .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("não checa senha quando usuário não é encontrado")
        void doesNotCheckPasswordWhenUserMissing() {
            when(userService.findByEmail(anyString())).thenReturn(null);

            assertThatThrownBy(() -> sut.authenticate("x@x.com", "senha"))
                .isInstanceOf(UsernameNotFoundException.class);

            verifyNoInteractions(passwordEncoder);
        }
    }
}
