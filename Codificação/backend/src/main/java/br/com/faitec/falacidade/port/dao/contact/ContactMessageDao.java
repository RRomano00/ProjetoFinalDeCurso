package br.com.faitec.falacidade.port.dao.contact;

import br.com.faitec.falacidade.domain.ContactMessage;

public interface ContactMessageDao {
    void save(ContactMessage msg);
}
