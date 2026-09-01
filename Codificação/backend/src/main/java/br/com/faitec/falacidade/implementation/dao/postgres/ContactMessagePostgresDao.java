package br.com.faitec.falacidade.implementation.dao.postgres;

import br.com.faitec.falacidade.domain.ContactMessage;
import br.com.faitec.falacidade.port.dao.contact.ContactMessageDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ContactMessagePostgresDao implements ContactMessageDao {

    private final Connection connection;

    public ContactMessagePostgresDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void save(ContactMessage msg) {
        String sql = "INSERT INTO contact_message (name, email, subject, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, msg.getName());
            ps.setString(2, msg.getEmail());
            ps.setString(3, msg.getSubject());
            ps.setString(4, msg.getMessage());
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar mensagem de contato", e);
        }
    }
}
