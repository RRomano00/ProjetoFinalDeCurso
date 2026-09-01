package br.com.faitec.falacidade.port.service.contact;

import br.com.faitec.falacidade.domain.ContactMessage;

public interface ContactService {
    void save(ContactMessage message);
}
