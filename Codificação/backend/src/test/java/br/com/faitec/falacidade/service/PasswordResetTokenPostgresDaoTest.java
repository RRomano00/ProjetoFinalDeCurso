package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.PasswordResetToken;
import br.com.faitec.falacidade.implementation.dao.postgres.PasswordResetTokenPostgresDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetTokenPostgresDao")
class PasswordResetTokenPostgresDaoTest {

    @Mock Connection        connection;
    @Mock PreparedStatement ps;
    @Mock ResultSet         rs;

    PasswordResetTokenPostgresDao sut;

    @BeforeEach
    void setUp() {
        sut = new PasswordResetTokenPostgresDao(connection);
    }

    // ================================================================
    // save()
    // ================================================================

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("insere token com userId, token e expiresAt corretos")
        void insertsCorrectly() throws Exception {
            when(connection.prepareStatement(contains("INSERT INTO password_reset_token"))).thenReturn(ps);

            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(1);
            token.setToken("uuid-123");
            token.setExpiresAt(LocalDateTime.of(2026, 6, 7, 12, 0));

            sut.save(token);

            verify(ps).setInt(1, 1);
            verify(ps).setString(2, "uuid-123");
            verify(ps).setTimestamp(eq(3), any(Timestamp.class));
            verify(ps).execute();
        }

        @Test
        @DisplayName("lança RuntimeException quando SQL falha")
        void throwsOnSQLException() throws Exception {
            when(connection.prepareStatement(anyString())).thenThrow(new SQLException("erro"));

            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(1);
            token.setToken("uuid");
            token.setExpiresAt(LocalDateTime.now().plusMinutes(30));

            assertThatThrownBy(() -> sut.save(token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("salvar token");
        }
    }

    // ================================================================
    // findByToken()
    // ================================================================

    @Nested
    @DisplayName("findByToken()")
    class FindByToken {

        @Test
        @DisplayName("retorna token quando encontrado")
        void found() throws Exception {
            when(connection.prepareStatement(contains("WHERE token"))).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true);
            when(rs.getInt("id")).thenReturn(1);
            when(rs.getInt("user_id")).thenReturn(10);
            when(rs.getString("token")).thenReturn("uuid-123");
            when(rs.getTimestamp("expires_at"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.now().plusMinutes(30)));
            when(rs.getBoolean("used")).thenReturn(false);

            PasswordResetToken result = sut.findByToken("uuid-123");

            assertThat(result).isNotNull();
            assertThat(result.getToken()).isEqualTo("uuid-123");
            assertThat(result.getUserId()).isEqualTo(10);
            assertThat(result.isUsed()).isFalse();
        }

        @Test
        @DisplayName("retorna null quando token não existe ou está marcado como used")
        void notFound() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(ps);
            when(ps.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);

            assertThat(sut.findByToken("inexistente")).isNull();
        }

        @Test
        @DisplayName("token expirado é detectado via isExpired() no domínio")
        void expiredTokenDetectedByDomain() {
            PasswordResetToken token = new PasswordResetToken();
            token.setExpiresAt(LocalDateTime.now().minusSeconds(1));

            assertThat(token.isExpired()).isTrue();
        }

        @Test
        @DisplayName("token ainda válido não é expirado")
        void validTokenNotExpired() {
            PasswordResetToken token = new PasswordResetToken();
            token.setExpiresAt(LocalDateTime.now().plusMinutes(30));

            assertThat(token.isExpired()).isFalse();
        }
    }

    // ================================================================
    // markUsed()
    // ================================================================

    @Nested
    @DisplayName("markUsed()")
    class MarkUsed {

        @Test
        @DisplayName("executa UPDATE com o tokenId correto")
        void updatesCorrectly() throws Exception {
            when(connection.prepareStatement(contains("SET used = true"))).thenReturn(ps);

            sut.markUsed(7);

            verify(ps).setInt(1, 7);
            verify(ps).execute();
        }

        @Test
        @DisplayName("lança RuntimeException quando SQL falha")
        void throwsOnSQLException() throws Exception {
            when(connection.prepareStatement(anyString())).thenThrow(new SQLException("erro"));

            assertThatThrownBy(() -> sut.markUsed(1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("marcar token");
        }
    }

    // ================================================================
    // deleteExpiredByUserId()
    // ================================================================

    @Nested
    @DisplayName("deleteExpiredByUserId()")
    class DeleteExpired {

        @Test
        @DisplayName("executa DELETE para o userId correto")
        void deletesForUser() throws Exception {
            when(connection.prepareStatement(contains("DELETE FROM password_reset_token"))).thenReturn(ps);

            sut.deleteExpiredByUserId(5);

            verify(ps).setInt(1, 5);
            verify(ps).execute();
        }

        @Test
        @DisplayName("lança RuntimeException quando SQL falha")
        void throwsOnSQLException() throws Exception {
            when(connection.prepareStatement(anyString())).thenThrow(new SQLException("erro"));

            assertThatThrownBy(() -> sut.deleteExpiredByUserId(1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("limpar tokens");
        }
    }
}
