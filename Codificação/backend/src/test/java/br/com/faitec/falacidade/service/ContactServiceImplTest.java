package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.ContactMessage;
import br.com.faitec.falacidade.implementation.service.contact.ContactServiceImpl;
import br.com.faitec.falacidade.port.dao.contact.ContactMessageDao;
import br.com.faitec.falacidade.port.service.email.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContactServiceImpl")
class ContactServiceImplTest {

    @Mock ContactMessageDao contactMessageDao;
    @Mock EmailService emailService;
    @InjectMocks ContactServiceImpl sut;

    private ContactMessage validMessage() {
        ContactMessage msg = new ContactMessage();
        msg.setName("João");
        msg.setEmail("joao@email.com");
        msg.setSubject("Dúvida");
        msg.setMessage("Como funciona o sistema?");
        return msg;
    }

    @Test
    @DisplayName("salva mensagem válida no DAO e notifica a equipe por e-mail")
    void savesValidMessage() {
        sut.save(validMessage());
        verify(contactMessageDao).save(any(ContactMessage.class));
        verify(emailService).sendContactNotification(
            any(), eq("João"), eq("joao@email.com"), eq("Dúvida"), eq("Como funciona o sistema?"));
    }

    @Test
    @DisplayName("mensagem com texto vazio lança IllegalArgumentException")
    void rejectsEmptyMessage() {
        ContactMessage msg = validMessage();
        msg.setMessage("   ");

        assertThatThrownBy(() -> sut.save(msg))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(contactMessageDao);
    }

    @Test
    @DisplayName("mensagem null lança IllegalArgumentException")
    void rejectsNullMessage() {
        ContactMessage msg = validMessage();
        msg.setMessage(null);

        assertThatThrownBy(() -> sut.save(msg))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(contactMessageDao);
    }
}
