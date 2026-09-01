package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.ContactMessage;
import br.com.faitec.falacidade.implementation.dao.postgres.ContactMessagePostgresDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContactMessagePostgresDao")
class ContactMessagePostgresDaoTest {

    @Mock Connection        connection;
    @Mock PreparedStatement ps;

    ContactMessagePostgresDao sut;

    @BeforeEach
    void setUp() {
        sut = new ContactMessagePostgresDao(connection);
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("insere todos os campos da mensagem")
        void insertsAllFields() throws Exception {
            when(connection.prepareStatement(contains("INSERT INTO contact_message"))).thenReturn(ps);

            ContactMessage msg = new ContactMessage();
            msg.setName("João");
            msg.setEmail("joao@email.com");
            msg.setSubject("Dúvida");
            msg.setMessage("Como funciona?");

            sut.save(msg);

            verify(ps).setString(1, "João");
            verify(ps).setString(2, "joao@email.com");
            verify(ps).setString(3, "Dúvida");
            verify(ps).setString(4, "Como funciona?");
            verify(ps).execute();
        }

        @Test
        @DisplayName("subject null é aceito (campo opcional)")
        void acceptsNullSubject() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(ps);

            ContactMessage msg = new ContactMessage();
            msg.setName("João");
            msg.setEmail("joao@email.com");
            msg.setSubject(null);
            msg.setMessage("Mensagem");

            assertThatCode(() -> sut.save(msg)).doesNotThrowAnyException();
            verify(ps).setString(3, null);
        }

        @Test
        @DisplayName("lança RuntimeException quando SQL falha")
        void throwsOnSQLException() throws Exception {
            when(connection.prepareStatement(anyString())).thenThrow(new SQLException("erro"));

            ContactMessage msg = new ContactMessage();
            msg.setName("X");
            msg.setEmail("x@x.com");
            msg.setMessage("msg");

            assertThatThrownBy(() -> sut.save(msg))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("contato");
        }
    }
}
