package br.com.faitec.falacidade.implementation.dao.postgres;

import br.com.faitec.falacidade.port.dao.occurrence.OccurrenceSupportDao;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OccurrenceSupportPostgresDao implements OccurrenceSupportDao {

    private final Connection connection;

    public OccurrenceSupportPostgresDao(Connection connection) { this.connection = connection; }

    @Override
    public boolean addSupport(int occurrenceId, int citizenId) {
        String sql = "INSERT INTO occurrence_support(occurrence_id, citizen_id) VALUES(?,?) " +
                     "ON CONFLICT (occurrence_id, citizen_id) DO NOTHING";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, occurrenceId);
            ps.setInt(2, citizenId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException("Erro ao registrar apoio", e); }
    }

    @Override
    public int countByOccurrence(int occurrenceId) {
        String sql = "SELECT COUNT(*) FROM occurrence_support WHERE occurrence_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, occurrenceId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException("Erro ao contar apoios", e); }
    }

    @Override
    public List<Integer> findOccurrenceIdsByCitizen(int citizenId) {
        String sql = "SELECT occurrence_id FROM occurrence_support WHERE citizen_id = ?";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, citizenId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getInt(1));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar apoios do cidadão", e); }
        return ids;
    }

    @Override
    public boolean hasSupported(int occurrenceId, int citizenId) {
        String sql = "SELECT 1 FROM occurrence_support WHERE occurrence_id = ? AND citizen_id = ? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, occurrenceId);
            ps.setInt(2, citizenId);
            return ps.executeQuery().next();
        } catch (SQLException e) { throw new RuntimeException("Erro ao verificar apoio", e); }
    }
}
