package br.com.faitec.falacidade.implementation.dao.postgres;

import br.com.faitec.falacidade.domain.PasswordResetToken;
import br.com.faitec.falacidade.port.dao.password.PasswordResetTokenDao;

import java.sql.*;

public class PasswordResetTokenPostgresDao implements PasswordResetTokenDao {

    private final Connection connection;

    public PasswordResetTokenPostgresDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void save(PasswordResetToken token) {
        String sql = "INSERT INTO password_reset_token (user_id, token, expires_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, token.getUserId());
            ps.setString(2, token.getToken());
            ps.setTimestamp(3, Timestamp.valueOf(token.getExpiresAt()));
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar token de recuperação", e);
        }
    }

    @Override
    public PasswordResetToken findByToken(String token) {
        String sql = "SELECT * FROM password_reset_token WHERE token = ? AND used = false LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PasswordResetToken t = new PasswordResetToken();
                t.setId(rs.getInt("id"));
                t.setUserId(rs.getInt("user_id"));
                t.setToken(rs.getString("token"));
                t.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                t.setUsed(rs.getBoolean("used"));
                return t;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar token", e);
        }
        return null;
    }

    @Override
    public void markUsed(int tokenId) {
        String sql = "UPDATE password_reset_token SET used = true WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, tokenId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao marcar token como usado", e);
        }
    }

    @Override
    public void deleteExpiredByUserId(int userId) {
        String sql = "DELETE FROM password_reset_token WHERE user_id = ? AND (expires_at < NOW() OR used = true)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao limpar tokens expirados", e);
        }
    }
}
