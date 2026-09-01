package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.service.email.EmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EmailServiceImpl envia e-mails HTML via MimeMessage + MimeMessageHelper.
 * O JavaMailSender é mockado; createMimeMessage() retorna um MimeMessage real
 * (sem Session) que o helper preenche e que validamos após o envio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl")
class EmailServiceImplTest {

    @Mock JavaMailSender mailSender;
    @InjectMocks EmailServiceImpl sut;

    private MimeMessage newMime() { return new MimeMessage((Session) null); }

    @org.junit.jupiter.api.BeforeEach
    void injectFrom() {
        ReflectionTestUtils.setField(sut, "fromEmail", "noreply@falacidade.com");
    }

    @Nested
    @DisplayName("sendPasswordResetEmail()")
    class SendReset {

        @Test
        @DisplayName("envia MimeMessage para o destinatário correto com assunto de recuperação")
        void sendsCorrectEmail() throws Exception {
            MimeMessage mime = newMime();
            when(mailSender.createMimeMessage()).thenReturn(mime);

            sut.sendPasswordResetEmail("user@test.com", "http://localhost/reset?token=abc");

            verify(mailSender).send(mime);
            assertThat(mime.getAllRecipients()).hasSize(1);
            assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("user@test.com");
            assertThat(mime.getFrom()[0].toString()).isEqualTo("noreply@falacidade.com");
            assertThat(mime.getSubject()).contains("Recuperação");
        }

        @Test
        @DisplayName("envia exatamente uma mensagem por chamada")
        void sendsExactlyOneMessage() {
            when(mailSender.createMimeMessage()).thenReturn(newMime());

            sut.sendPasswordResetEmail("a@b.com", "http://link");

            verify(mailSender, times(1)).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("sendWelcomeEmail()")
    class SendWelcome {

        @Test
        @DisplayName("envia e-mail de boas-vindas com assunto e destinatário corretos")
        void sendsWelcomeWithName() throws Exception {
            MimeMessage mime = newMime();
            when(mailSender.createMimeMessage()).thenReturn(mime);

            sut.sendWelcomeEmail("novo@test.com", "Maria");

            verify(mailSender).send(mime);
            assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("novo@test.com");
            assertThat(mime.getSubject()).contains("Bem-vindo");
        }
    }
}
