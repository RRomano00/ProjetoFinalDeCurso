package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.dao.postgres.configuration.PostgresConnectionManagerConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Reset da verificação em duas etapas ao subir o projeto")
class MfaResetOnStartupTest {

    @Test
    @DisplayName("ligado: zera todos os métodos (aplicativo, e-mail e SMS) de todas as contas")
    void resetsEveryMethod() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(4);

        assertThat(PostgresConnectionManagerConfiguration.resetMfa(connection, true)).isEqualTo(4);

        verify(connection).prepareStatement(argThat(sql -> sql.startsWith("UPDATE \"user\" SET")
            && sql.contains("mfa_enabled=false") && sql.contains("mfa_setup_done=false")
            && sql.contains("mfa_secret=NULL") && sql.contains("mfa_email_enabled=false")
            && sql.contains("mfa_sms_phone=NULL") && !sql.contains("WHERE")));
    }

    @Test
    @DisplayName("desligado (padrão): não mexe no banco")
    void offByDefault() throws Exception {
        Connection connection = mock(Connection.class);

        assertThat(PostgresConnectionManagerConfiguration.resetMfa(connection, false)).isZero();

        verifyNoInteractions(connection);
    }
}
