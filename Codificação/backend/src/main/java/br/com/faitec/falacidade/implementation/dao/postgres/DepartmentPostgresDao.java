package br.com.faitec.falacidade.implementation.dao.postgres;

import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.port.dao.department.DepartmentDao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DepartmentPostgresDao implements DepartmentDao {

    private static final String FIELDS = "id, name, email, city, state";

    private final Connection connection;

    public DepartmentPostgresDao(Connection connection) {
        this.connection = connection;
    }

    @Override
    public int add(Department entity) {
        String sql = "INSERT INTO department (name, email, city, state) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getEmail());
            ps.setString(3, entity.getCity());
            ps.setString(4, entity.getState());
            ps.execute();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return -1;
        } catch (SQLException e) {
            // 23505 = unique_violation: e-mail repetido, ou nome repetido dentro do
            // mesmo município. A restrição do banco é a última palavra, mesmo quando
            // duas requisições chegam juntas.
            if ("23505".equals(e.getSQLState())) return -1;
            throw new RuntimeException("Erro ao cadastrar departamento", e);
        }
    }

    /**
     * A restrição do banco é quem julga a duplicidade também aqui — e só ela
     * sabe isentar o próprio registro, que uma consulta prévia acusaria.
     */
    @Override
    public boolean update(Department entity) {
        String sql = "UPDATE department SET name=?, email=?, city=?, state=? WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getEmail());
            ps.setString(3, entity.getCity());
            ps.setString(4, entity.getState());
            ps.setInt(5, entity.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) return false;
            throw new RuntimeException("Erro ao editar departamento", e);
        }
    }

    @Override
    public List<Department> readall() {
        return query("SELECT " + FIELDS + " FROM department ORDER BY state, city, name");
    }

    @Override
    public List<Department> readAllByCity(String city, String state) {
        return query("SELECT " + FIELDS + " FROM department " +
                     "WHERE lower(city) = lower(?) AND upper(state) = upper(?) ORDER BY name",
                     city, state);
    }

    @Override
    public Department readById(int id) {
        String sql = "SELECT " + FIELDS + " FROM department WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar departamento", e);
        }
        return null;
    }

    /** "Secretaria de Obras" pode existir em cada município; duas vezes no mesmo, não. */
    @Override
    public boolean existsByNameInCity(String name, String city, String state) {
        return exists("SELECT 1 FROM department WHERE lower(name) = lower(?) " +
                      "AND lower(city) = lower(?) AND upper(state) = upper(?)", name, city, state);
    }

    /** O e-mail é o destino do encaminhamento: único no sistema inteiro. */
    @Override
    public boolean existsByEmail(String email) {
        return exists("SELECT 1 FROM department WHERE lower(email) = lower(?)", email);
    }

    // ---- helpers ----

    private List<Department> query(String sql, String... params) {
        List<Department> departments = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) departments.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar departamentos", e);
        }
        return departments;
    }

    private boolean exists(String sql, String... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar departamento existente", e);
        }
    }

    private Department mapRow(ResultSet rs) throws SQLException {
        return new Department(rs.getInt("id"), rs.getString("name"), rs.getString("email"),
                              rs.getString("city"), rs.getString("state"));
    }
}
