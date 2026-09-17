package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.PasswordResetToken;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.password.PasswordResetServiceImpl;
import br.com.faitec.falacidade.implementation.service.tracking.AnonymousTrackingCodeService;
import br.com.faitec.falacidade.port.dao.password.PasswordResetTokenDao;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetServiceImpl – RF05")
class PasswordResetServiceImplTest {

    @Mock UserService userService;
    @Mock PasswordResetTokenDao tokenDao;
    @Mock EmailService emailService;
    @Mock PasswordEncoder passwordEncoder;
    @Spy  AnonymousTrackingCodeService codeService = new AnonymousTrackingCodeService();

    @InjectMocks
    PasswordResetServiceImpl sut;

    @BeforeEach
    void injectProperties() {
        ReflectionTestUtils.setField(sut, "expirationMinutes", 30);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private UserModel user(int id, String email) {
        UserModel u = new UserModel();
        u.setId(id);
        u.setEmail(email);
        u.setFullname("Teste");
        return u;
    }

    private PasswordResetToken validToken(int userId) {
        PasswordResetToken t = new PasswordResetToken();
        t.setId(1);
        t.setUserId(userId);
        t.setToken("A3KP7NB2");
        t.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        t.setUsed(false);
        return t;
    }

    private PasswordResetToken expiredToken(int userId) {
        PasswordResetToken t = validToken(userId);
        t.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return t;
    }

    private PasswordResetToken usedToken(int userId) {
        PasswordResetToken t = validToken(userId);
        t.setUsed(true);
        return t;
    }

    // ================================================================
    // requestReset()
    // ================================================================

    @Nested
    @DisplayName("requestReset()")
    class RequestReset {

        @Test
        @DisplayName("gera token, salva e envia e-mail quando e-mail existe")
        void success() {
            when(userService.findByEmail("joao@email.com")).thenReturn(user(1, "joao@email.com"));

            sut.requestReset("joao@email.com");

            // Deve limpar tokens antigos
            verify(tokenDao).deleteExpiredByUserId(1);

            // Deve salvar novo token
            ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenDao).save(tokenCaptor.capture());

            PasswordResetToken saved = tokenCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(1);
            assertThat(saved.getToken()).isNotBlank();
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());

            // Deve enviar por e-mail o mesmo código que a tela pede de volta
            ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordResetEmail(
                eq("joao@email.com"), codeCaptor.capture());
            assertThat(codeCaptor.getValue())
                .isEqualTo(saved.getToken())
                .matches("[A-Z2-9]{8}");
        }

        @Test
        @DisplayName("e-mail inexistente → não faz nada (não revela se existe – segurança)")
        void silentlyIgnoresUnknownEmail() {
            when(userService.findByEmail("nao@existe.com")).thenReturn(null);

            sut.requestReset("nao@existe.com");

            verifyNoInteractions(tokenDao);
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("código gerado deve ser diferente a cada pedido")
        void tokenIsUnique() {
            when(userService.findByEmail("a@b.com")).thenReturn(user(1, "a@b.com"));

            sut.requestReset("a@b.com");
            sut.requestReset("a@b.com");

            ArgumentCaptor<PasswordResetToken> captor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenDao, times(2)).save(captor.capture());

            String token1 = captor.getAllValues().get(0).getToken();
            String token2 = captor.getAllValues().get(1).getToken();
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // ================================================================
    // confirmReset()
    // ================================================================

    @Nested
    @DisplayName("confirmReset()")
    class ConfirmReset {

        @Test
        @DisplayName("altera senha e marca token como usado quando tudo está válido")
        void success() {
            when(tokenDao.findByToken("A3KP7NB2")).thenReturn(validToken(1));
            when(passwordEncoder.encode("Nova@Senha9")).thenReturn("$2a$ENCODED");
            when(userService.updatePasswordEncoded(1, "$2a$ENCODED")).thenReturn(true);

            boolean result = sut.confirmReset("A3KP7NB2", "Nova@Senha9");

            assertThat(result).isTrue();
            verify(tokenDao).markUsed(1);
        }

        @Test
        @DisplayName("código digitado com espaço e em minúsculas continua valendo")
        void normalizesTypedCode() {
            when(tokenDao.findByToken("A3KP7NB2")).thenReturn(validToken(1));
            when(passwordEncoder.encode("Nova@Senha9")).thenReturn("$2a$ENCODED");
            when(userService.updatePasswordEncoded(1, "$2a$ENCODED")).thenReturn(true);

            assertThat(sut.confirmReset("  a3kp7nb2 ", "Nova@Senha9")).isTrue();
        }

        @Test
        @DisplayName("token inexistente → retorna false")
        void tokenNotFound() {
            when(tokenDao.findByToken("INVALIDO")).thenReturn(null);
            assertThat(sut.confirmReset("INVALIDO", "Nova@Senha9")).isFalse();
            verify(tokenDao, never()).markUsed(anyInt());
        }

        @Test
        @DisplayName("token expirado → retorna false")
        void expiredTokenReturnsFalse() {
            when(tokenDao.findByToken("A3KP7NB2")).thenReturn(expiredToken(1));
            assertThat(sut.confirmReset("A3KP7NB2", "Nova@Senha9")).isFalse();
            verify(tokenDao, never()).markUsed(anyInt());
        }

        @Test
        @DisplayName("token já usado → retorna false")
        void usedTokenReturnsFalse() {
            when(tokenDao.findByToken("A3KP7NB2")).thenReturn(usedToken(1));
            assertThat(sut.confirmReset("A3KP7NB2", "Nova@Senha9")).isFalse();
            verify(tokenDao, never()).markUsed(anyInt());
        }

        @Test
        @DisplayName("nova senha sem caractere especial → retorna false")
        void invalidPassword() {
            when(tokenDao.findByToken("A3KP7NB2")).thenReturn(validToken(1));
            assertThat(sut.confirmReset("A3KP7NB2", "SenhaSemEspecial1")).isFalse();
            verifyNoInteractions(passwordEncoder, userService);
        }

        @Test
        @DisplayName("nova senha menor que 8 chars → retorna false")
        void shortPassword() {
            when(tokenDao.findByToken("A3KP7NB2")).thenReturn(validToken(1));
            assertThat(sut.confirmReset("A3KP7NB2", "Ab@1")).isFalse();
        }

        @Test
        @DisplayName("quando updatePasswordEncoded falha → não marca token como usado")
        void doesNotMarkUsedIfUpdateFails() {
            when(tokenDao.findByToken("A3KP7NB2")).thenReturn(validToken(1));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$ENCODED");
            when(userService.updatePasswordEncoded(1, "$2a$ENCODED")).thenReturn(false);

            boolean result = sut.confirmReset("A3KP7NB2", "Nova@Senha9");

            assertThat(result).isFalse();
            verify(tokenDao, never()).markUsed(anyInt());
        }
    }
}
