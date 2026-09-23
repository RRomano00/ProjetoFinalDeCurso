package br.com.faitec.falacidade.implementation.dao.postgres;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.dao.user.UserDao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserPostgresDao implements UserDao {

    private static final Logger logger = Logger.getLogger(UserPostgresDao.class.getName());
    private final Connection connection;

    public UserPostgresDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public int add(UserModel entity) {
        String sql =
            "INSERT INTO \"user\" " +
            "(password, fullname, email, date_of_birth, phone_number, " +
            " street, neighborhood, number, cep, city, state, role, is_active, accepts_terms, mfa_email_enabled) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, entity.getPassword());
                ps.setString(2, entity.getFullname());
                ps.setString(3, entity.getEmail());
                if (entity.getDateOfBirth() != null)
                    ps.setDate(4, Date.valueOf(entity.getDateOfBirth()));
                else ps.setNull(4, Types.DATE);
                ps.setString(5, entity.getPhoneNumber());
                ps.setString(6, entity.getStreet());
                ps.setString(7, entity.getNeighborhood());
                ps.setString(8, entity.getNumber());
                ps.setString(9, entity.getCep());
                ps.setString(10, entity.getCity());
                ps.setString(11, entity.getState());
                ps.setString(12, entity.getRole().name());
                ps.setBoolean(13, entity.isActive());
                ps.setBoolean(14, entity.isAcceptsTerms());
                ps.setBoolean(15, entity.isMfaEmailEnabled());
                ps.execute();
                ResultSet keys = ps.getGeneratedKeys();
                int id = 0;
                if (keys.next()) id = keys.getInt(1);
                connection.commit();
                return id;
            }
        } catch (SQLException e) {
            rollback();
            if ("23505".equals(e.getSQLState()))
                throw new IllegalStateException("Este e-mail já está cadastrado.", e);
            throw new RuntimeException("Erro ao inserir usuário: " + e.getMessage(), e);
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public void remove(int id) {
        logger.log(Level.INFO, "Removendo usuário id={0}", id);
        execute("DELETE FROM \"user\" WHERE id = ?", id);
    }

    @Override
    public UserModel readById(int id) {
        return queryOne("SELECT * FROM \"user\" WHERE id = ?", id);
    }

    @Override
    public List<UserModel> readall() {
        List<UserModel> users = new ArrayList<>();
        String sql = "SELECT * FROM \"user\" WHERE is_active = true ORDER BY fullname";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) users.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar usuários", e);
        }
        return users;
    }

    @Override
    public List<UserModel> readAllUsers() {
        List<UserModel> users = new ArrayList<>();
        String sql = "SELECT * FROM \"user\" ORDER BY fullname";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) users.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar usuários", e);
        }
        return users;
    }

    @Override
    public void setActive(int userId, boolean active) {
        String sql = "UPDATE \"user\" SET is_active=?, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, userId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ativar/inativar usuário", e);
        }
    }

    @Override
    public void updateInformation(int id, UserModel entity) {
        String sql =
            "UPDATE \"user\" SET fullname=?, phone_number=?, street=?, " +
            "neighborhood=?, number=?, cep=?, city=?, state=?, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.getFullname());
            ps.setString(2, entity.getPhoneNumber());
            ps.setString(3, entity.getStreet());
            ps.setString(4, entity.getNeighborhood());
            ps.setString(5, entity.getNumber());
            ps.setString(6, entity.getCep());
            ps.setString(7, entity.getCity());
            ps.setString(8, entity.getState());
            ps.setInt(9, id);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar usuário", e);
        }
    }

    @Override
    public void setRole(int userId, UserModel.UserRole role) {
        String sql = "UPDATE \"user\" SET role=?, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setInt(2, userId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao alterar o perfil do usuário", e);
        }
    }

    @Override
    public UserModel readByEmail(String email) {
        return queryOne("SELECT * FROM \"user\" WHERE email = ? AND is_active = true", email);
    }

    @Override
    public boolean updatePassword(int id, String encodedPassword) {
        String sql = "UPDATE \"user\" SET password=?, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, encodedPassword);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar senha", e);
        }
    }

    @Override
    public void updateMfaSecret(int userId, String secret) {
        String sql = "UPDATE \"user\" SET mfa_secret=?, mfa_setup_done=false, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, secret);
            ps.setInt(2, userId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar secret MFA", e);
        }
    }

    @Override
    public void enableMfa(int userId) {
        String sql = "UPDATE \"user\" SET mfa_enabled=true, mfa_setup_done=true, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ativar MFA", e);
        }
    }

    @Override
    public void disableMfa(int userId) {
        String sql = "UPDATE \"user\" SET mfa_enabled=false, mfa_setup_done=false, mfa_secret=NULL, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao desativar MFA", e);
        }
    }

    @Override
    public void setEmailMfa(int userId, boolean enabled) {
        String sql = "UPDATE \"user\" SET mfa_email_enabled=?, updated_at=NOW() WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setInt(2, userId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar MFA por e-mail", e);
        }
    }

    @Override
    public boolean existsStaffInCity(String city, String state) {
        if (city == null || city.isBlank()) return false;
        String sql = "SELECT 1 FROM \"user\" WHERE is_active = true " +
                     "AND role IN ('EMPLOYEE','ADMINISTRATOR') AND lower(city) = lower(?) " +
                     "AND (? = '' OR state IS NULL OR upper(state) = upper(?)) LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String uf = state == null ? "" : state.trim();
            ps.setString(1, city.trim());
            ps.setString(2, uf);
            ps.setString(3, uf);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar equipe do município", e);
        }
    }

    private UserModel queryOne(String sql, Object param) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (param instanceof Integer i) ps.setInt(1, i);
            else ps.setString(1, param.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário", e);
        }
        return null;
    }

    private void execute(String sql, int id) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar query", e);
        }
    }

    private UserModel mapRow(ResultSet rs) throws SQLException {
        UserModel u = new UserModel();
        u.setId(rs.getInt("id"));
        u.setFullname(rs.getString("fullname"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));
        Date dob = rs.getDate("date_of_birth");
        if (dob != null) u.setDateOfBirth(dob.toLocalDate());
        u.setPhoneNumber(rs.getString("phone_number"));
        u.setStreet(rs.getString("street"));
        u.setNeighborhood(rs.getString("neighborhood"));
        u.setNumber(rs.getString("number"));
        u.setCep(rs.getString("cep"));
        u.setCity(rs.getString("city"));
        u.setState(rs.getString("state"));
        u.setActive(rs.getBoolean("is_active"));
        u.setAcceptsTerms(rs.getBoolean("accepts_terms"));
        String roleStr = rs.getString("role");
        if ("USER".equals(roleStr)) roleStr = "CITIZEN";
        if (roleStr != null) u.setRole(UserModel.UserRole.valueOf(roleStr));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) u.setCreatedAt(cat.toLocalDateTime());
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) u.setUpdatedAt(uat.toLocalDateTime());

        u.setMfaEnabled(rs.getBoolean("mfa_enabled"));
        u.setMfaSetupDone(rs.getBoolean("mfa_setup_done"));
        u.setMfaEmailEnabled(rs.getBoolean("mfa_email_enabled"));
        String secret = rs.getString("mfa_secret");
        u.setMfaSecret(secret);

        return u;
    }

    private void restoreAutoCommit() {
        try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
    }

    private void rollback() {
        try { connection.rollback(); } catch (SQLException ignored) {}
    }
}
