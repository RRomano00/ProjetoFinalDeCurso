package br.com.faitec.falacidade.implementation.service.contact;

import br.com.faitec.falacidade.domain.ContactMessage;
import br.com.faitec.falacidade.port.dao.contact.ContactMessageDao;
import br.com.faitec.falacidade.port.service.contact.ContactService;
import br.com.faitec.falacidade.port.service.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContactServiceImpl implements ContactService {

    private final ContactMessageDao contactMessageDao;
    private final EmailService emailService;

    /** E-mail da equipe que recebe as mensagens do formulário de contato. */
    @Value("${app.contact-recipient:fala.cidade.faitec@gmail.com}")
    private String contactRecipient;

    public ContactServiceImpl(ContactMessageDao contactMessageDao, EmailService emailService) {
        this.contactMessageDao = contactMessageDao;
        this.emailService = emailService;
    }

    @Override
    public void save(ContactMessage message) {
        if (message.getMessage() == null || message.getMessage().isBlank()) {
            throw new IllegalArgumentException("Mensagem não pode ser vazia");
        }
        contactMessageDao.save(message);

        // Notifica a equipe por e-mail (envio assíncrono — não bloqueia/falha o salvamento).
        emailService.sendContactNotification(
            contactRecipient,
            message.getName(),
            message.getEmail(),
            message.getSubject(),
            message.getMessage()
        );
    }
}
