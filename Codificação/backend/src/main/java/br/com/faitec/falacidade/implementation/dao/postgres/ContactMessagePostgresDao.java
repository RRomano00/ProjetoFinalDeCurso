package br.com.faitec.falacidade.implementation.dao.postgres;

import br.com.faitec.falacidade.domain.ContactMessage;
import br.com.faitec.falacidade.port.dao.contact.ContactMessageDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public class ContactMessagePostgresDao implements ContactMessageDao {

    private final DataSource dataSource;

    public ContactMessagePostgresDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(ContactMessage msg) {
        String sql = "INSERT INTO contact_message (name, email, subject, message) VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
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
