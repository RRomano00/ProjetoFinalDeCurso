package br.com.faitec.falacidade.implementation.dao.postgres;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.OccurrenceMedia;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto;
import br.com.faitec.falacidade.port.dao.occurrence.OccurrenceDao;

import java.sql.*;
import java.util.ArrayList;
import java.security.SecureRandom;
import java.util.List;

public class OccurrencePostgresDao implements OccurrenceDao {

    private final Connection connection;

    public OccurrencePostgresDao(Connection connection) { this.connection = connection; }

    private static final String SELECT_FIELDS =
        "SELECT o.*, u.fullname, u.email AS user_email, " +
        "(SELECT COUNT(*) FROM occurrence_support s WHERE s.occurrence_id = o.id) AS support_count " +
        "FROM occurrence o ";

    /** Os mesmos descartes do código de acompanhamento anônimo, pelo mesmo
     *  motivo: O, 0, I e 1 são ambíguos em fonte sem serifa. */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int    SORTEADOS = 5;
    private static final SecureRandom SORTE = new SecureRandom();

    /**
     * Protocolo no formato FC-26-K8RQ3: a marca, o ano e cinco símbolos
     * sorteados — onze caracteres, lidos em três blocos.
     *
     * A data completa saiu porque o registro já mostra data e hora ao lado do
     * protocolo: os oito dígitos não acrescentavam nada e faziam o código ser
     * lido e digitado errado. Os cinco símbolos vinham de um UUID, em
     * hexadecimal, e traziam justamente o 0 e o 1 que o resto do sistema
     * descarta.
     *
     * Sem a data, o sorteio deixa de ser disputado entre as ocorrências do mesmo
     * dia e passa a valer para o ano inteiro, então o código é conferido no
     * banco antes de ser usado. A coluna é UNIQUE: a conferência evita a
     * exceção, e a exceção segue como garantia final.
     */
    private String generateProtocol() {
        String ano = String.format("%02d", java.time.Year.now().getValue() % 100);
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            StringBuilder sorteio = new StringBuilder(SORTEADOS);
            for (int i = 0; i < SORTEADOS; i++)
                sorteio.append(ALFABETO.charAt(SORTE.nextInt(ALFABETO.length())));
            String protocolo = "FC-" + ano + "-" + sorteio;
            if (!protocoloEmUso(protocolo)) return protocolo;
        }
        throw new IllegalStateException("Não foi possível sortear um protocolo livre");
    }

    private boolean protocoloEmUso(String protocolo) {
        try (PreparedStatement ps = connection.prepareStatement(
                 "SELECT 1 FROM occurrence WHERE protocol_number = ?")) {
            ps.setString(1, protocolo);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao conferir o protocolo", e);
        }
    }

    @Override
    public int add(Occurrence entity) {
        String findUser = "SELECT id FROM \"user\" WHERE email = ? LIMIT 1";
        String sql =
            "INSERT INTO occurrence " +
            "(protocol_number,title,description,number,street,neighborhood,address_reference,city," +
            " latitude,longitude,url_media,cloudinary_public_id,image_blurred," +
            " type,status,priority,is_anonymous,anonymous_tracking_code_hash,user_id,ip_address,group_id," +
            " state)" +
            " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try {
            connection.setAutoCommit(false);
            Integer userId = null;
            if (entity.getEmail() != null && !entity.getEmail().isBlank()
                    && !"anonimo".equalsIgnoreCase(entity.getEmail())) {
                try (PreparedStatement ps = connection.prepareStatement(findUser)) {
                    ps.setString(1, entity.getEmail());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) userId = rs.getInt("id");
                    else throw new RuntimeException("Usuário não encontrado: " + entity.getEmail());
                }
            }
            if (entity.getPriority() == null && entity.getType() != null)
                entity.setPriority(Occurrence.Priority.fromType(entity.getType()));

            String protocol = generateProtocol();
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, protocol);
                ps.setString(2, entity.getTitle());
                ps.setString(3, entity.getDescription());
                ps.setString(4, entity.getNumber());
                ps.setString(5, entity.getStreet());
                ps.setString(6, entity.getNeighborhood());
                ps.setString(7, entity.getAddressReference());
                ps.setString(8, entity.getCity());
                if (entity.getLatitude()  != null) ps.setDouble(9,  entity.getLatitude());  else ps.setNull(9,  Types.DOUBLE);
                if (entity.getLongitude() != null) ps.setDouble(10, entity.getLongitude()); else ps.setNull(10, Types.DOUBLE);
                ps.setString(11, entity.getUrlMedia());
                ps.setString(12, entity.getCloudinaryPublicId());
                ps.setBoolean(13, entity.isImageBlurred());
                ps.setString(14, entity.getType().name());
                ps.setString(15, entity.getStatus().name());
                ps.setString(16, entity.getPriority().name());
                ps.setBoolean(17, entity.isAnonymous());
                if (entity.getAnonymousTrackingCodeHash() != null) ps.setString(18, entity.getAnonymousTrackingCodeHash());
                else ps.setNull(18, Types.VARCHAR);
                if (userId != null) ps.setInt(19, userId); else ps.setNull(19, Types.INTEGER);
                if (entity.isAnonymous() && entity.getIpAddress() != null) ps.setString(20, entity.getIpAddress());
                else ps.setNull(20, Types.VARCHAR);
                if (entity.getGroupId() != null) ps.setInt(21, entity.getGroupId());
                else ps.setNull(21, Types.INTEGER);
                ps.setString(22, entity.getState());
                ps.execute();
                ResultSet keys = ps.getGeneratedKeys();
                int id = keys.next() ? keys.getInt(1) : 0;
                insertMedia(id, entity.getMedia());
                entity.setProtocolNumber(protocol);
                connection.commit();
                return id;
            }
        } catch (SQLException e) { rollback(); throw new RuntimeException("Erro ao inserir ocorrência", e); }
        finally { restoreAutoCommit(); }
    }

    private void insertMedia(int occurrenceId, List<OccurrenceMedia> media) throws SQLException {
        if (media == null || media.isEmpty()) return;
        String sql = "INSERT INTO occurrence_media(occurrence_id, url, cloudinary_public_id, image_blurred) VALUES(?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (OccurrenceMedia m : media) {
                if (m == null || m.getUrl() == null || m.getUrl().isBlank()) continue;
                ps.setInt(1, occurrenceId);
                ps.setString(2, m.getUrl());
                ps.setString(3, m.getCloudinaryPublicId());
                ps.setBoolean(4, m.isImageBlurred());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override public GetOccurrenceDto readById(int id) {
        GetOccurrenceDto dto = queryOne(SELECT_FIELDS +
                        "LEFT JOIN \"user\" u ON o.user_id=u.id WHERE o.id=?", id);
        if (dto != null) dto.setMedia(readMedia(id));
        return dto;
    }

    private List<OccurrenceMedia> readMedia(int occurrenceId) {
        List<OccurrenceMedia> list = new ArrayList<>();
        String sql = "SELECT url, cloudinary_public_id, image_blurred FROM occurrence_media WHERE occurrence_id=? ORDER BY id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, occurrenceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(new OccurrenceMedia(rs.getString("url"), rs.getString("cloudinary_public_id"),
                                             rs.getBoolean("image_blurred")));
        } catch (SQLException e) { throw new RuntimeException("Erro ao carregar fotos da ocorrência", e); }
        return list;
    }

    @Override
    public List<GetOccurrenceDto> readGroup(int rootId) {
        List<GetOccurrenceDto> list = new ArrayList<>();
        String sql = SELECT_FIELDS +
                     "LEFT JOIN \"user\" u ON o.user_id=u.id " +
                     "WHERE o.id=? OR o.group_id=? ORDER BY o.created_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, rootId);
            ps.setInt(2, rootId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao carregar grupo de ocorrências", e); }
        return list;
    }

    @Override
    public List<OccurrenceHistoryDto> readHistory(int occurrenceId) {
        List<OccurrenceHistoryDto> list = new ArrayList<>();
        String sql = "SELECT h.old_status, h.new_status, h.observation, h.changed_at, u.fullname " +
                     "FROM occurrence_history h LEFT JOIN \"user\" u ON h.changed_by=u.id " +
                     "WHERE h.occurrence_id=? ORDER BY h.changed_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, occurrenceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                OccurrenceHistoryDto h = new OccurrenceHistoryDto();
                h.setOldStatus(rs.getString("old_status"));
                h.setNewStatus(rs.getString("new_status"));
                h.setObservation(rs.getString("observation"));
                h.setChangedByName(rs.getString("fullname"));
                Timestamp t = rs.getTimestamp("changed_at");
                if (t != null) h.setChangedAt(t.toLocalDateTime());
                list.add(h);
            }
        } catch (SQLException e) { throw new RuntimeException("Erro ao carregar histórico da ocorrência", e); }
        return list;
    }

    @Override public List<GetOccurrenceDto> readall() {
        return queryList(SELECT_FIELDS +
                         "LEFT JOIN \"user\" u ON o.user_id=u.id ORDER BY o.created_at DESC");
    }

    /**
     * Busca pelo protocolo exatamente como foi gravado e, se não achar, de novo
     * ignorando caixa e traços — quem copia de um e-mail ou anota no papel erra
     * nesses dois. A primeira consulta usa o índice da coluna; a segunda, que
     * aplica função sobre ela e não usa, só roda quando a primeira falha.
     */
    @Override public GetOccurrenceDto readByProtocolNumber(String protocol) {
        GetOccurrenceDto achado = queryOne(SELECT_FIELDS +
                        "LEFT JOIN \"user\" u ON o.user_id=u.id WHERE o.protocol_number=?", protocol);
        if (achado != null || protocol == null) return achado;
        String solto = protocol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return queryOne(SELECT_FIELDS +
                        "LEFT JOIN \"user\" u ON o.user_id=u.id " +
                        "WHERE UPPER(REPLACE(o.protocol_number, '-', '')) = ?", solto);
    }

    @Override public GetOccurrenceDto findByAnonymousTrackingCodeHash(String hash) {
        return queryOne(SELECT_FIELDS +
                        "LEFT JOIN \"user\" u ON o.user_id=u.id WHERE o.anonymous_tracking_code_hash=?", hash);
    }

    @Override public List<GetOccurrenceDto> findNearby(double lat, double lon, String type, double radius) {
        double dLat = radius / 111_111.0;
        double dLon = radius / (111_111.0 * Math.cos(Math.toRadians(lat)));
        String sql  = SELECT_FIELDS +
                      "LEFT JOIN \"user\" u ON o.user_id=u.id " +
                      "WHERE o.status NOT IN ('CONCLUIDA','INDEFERIDA') AND o.type=? " +
                      "AND o.latitude BETWEEN ? AND ? AND o.longitude BETWEEN ? AND ?";
        List<GetOccurrenceDto> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setDouble(2, lat - dLat); ps.setDouble(3, lat + dLat);
            ps.setDouble(4, lon - dLon); ps.setDouble(5, lon + dLon);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar ocorrências próximas", e); }
        return list;
    }

    @Override public void updateOccurrenceStatusToInProgress(int id) { simpleUpdate(id, "EM_ANDAMENTO"); }
    @Override public void updateOccurrenceStatusToConclude(int id)    { simpleUpdate(id, "CONCLUIDA"); }

    @Override public void updateStatus(int id, String newStatus, int changedBy, String obs,
                                       Integer departmentId) {
        try {
            connection.setAutoCommit(false);
            String old = null;
            try (PreparedStatement ps = connection.prepareStatement("SELECT status FROM occurrence WHERE id=?")) {
                ps.setInt(1, id); ResultSet rs = ps.executeQuery();
                if (rs.next()) old = rs.getString("status");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE occurrence SET status=?,updated_at=NOW() WHERE id=?")) {
                ps.setString(1, newStatus); ps.setInt(2, id); ps.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO occurrence_history(occurrence_id,changed_by,old_status,new_status,observation,department_id)" +
                    " VALUES(?,?,?,?,?,?)")) {
                ps.setInt(1, id); ps.setInt(2, changedBy);
                ps.setString(3, old); ps.setString(4, newStatus); ps.setString(5, obs);
                if (departmentId != null) ps.setInt(6, departmentId); else ps.setNull(6, java.sql.Types.INTEGER);
                ps.execute();
            }
            connection.commit();
        } catch (SQLException e) { rollback(); throw new RuntimeException("Erro ao atualizar status", e); }
        finally { restoreAutoCommit(); }
    }

    private void simpleUpdate(int id, String status) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE occurrence SET status=?,updated_at=NOW() WHERE id=?")) {
            ps.setString(1, status); ps.setInt(2, id); ps.execute();
        } catch (SQLException e) { throw new RuntimeException("Erro ao atualizar status", e); }
    }

    private GetOccurrenceDto queryOne(String sql, Object param) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (param instanceof Integer i) ps.setInt(1, i); else ps.setString(1, param.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        } catch (SQLException e) { throw new RuntimeException("Erro ao buscar ocorrência", e); }
    }

    private List<GetOccurrenceDto> queryList(String sql) {
        List<GetOccurrenceDto> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar ocorrências", e); }
        return list;
    }

    private GetOccurrenceDto mapRow(ResultSet rs) throws SQLException {
        GetOccurrenceDto o = new GetOccurrenceDto();
        o.setId(rs.getInt("id"));
        o.setProtocolNumber(rs.getString("protocol_number"));
        o.setTitle(rs.getString("title"));
        o.setDescription(rs.getString("description"));
        o.setNumber(rs.getString("number"));
        o.setStreet(rs.getString("street"));
        o.setNeighborhood(rs.getString("neighborhood"));
        o.setAddressReference(rs.getString("address_reference"));
        o.setCity(rs.getString("city"));
        o.setState(rs.getString("state"));
        double lat = rs.getDouble("latitude");  if (!rs.wasNull()) o.setLatitude(lat);
        double lon = rs.getDouble("longitude"); if (!rs.wasNull()) o.setLongitude(lon);
        o.setUrlMedia(rs.getString("url_media"));
        o.setImageBlurred(rs.getBoolean("image_blurred"));
        o.setType(Occurrence.OccurrenceType.valueOf(rs.getString("type")));
        o.setStatus(Occurrence.OccurrenceStatus.valueOf(rs.getString("status")));
        o.setPriority(Occurrence.Priority.valueOf(rs.getString("priority")));
        o.setAnonymous(rs.getBoolean("is_anonymous"));
        int gid = rs.getInt("group_id"); if (!rs.wasNull()) o.setGroupId(gid);
        o.setSupportCount(rs.getInt("support_count"));
        o.setEmail(rs.getString("user_email"));
        // user_id é ON DELETE SET NULL: se o autor apagou a conta, a ocorrência fica
        // sem nome/e-mail. Identifica o autor perdido em vez de exibir um campo vazio.
        String fullname = rs.getString("fullname");
        o.setFullname(!o.isAnonymous() && fullname == null ? "Usuário Desconhecido" : fullname);
        Timestamp cat = rs.getTimestamp("created_at"); if (cat != null) o.setCreatedAt(cat.toLocalDateTime());
        Timestamp uat = rs.getTimestamp("updated_at"); if (uat != null) o.setUpdatedAt(uat.toLocalDateTime());
        return o;
    }

    @Override
    public List<GetOccurrenceDto> readAllByUserEmail(String email) {
        List<GetOccurrenceDto> list = new ArrayList<>();
        String sql = SELECT_FIELDS +
                     "LEFT JOIN \"user\" u ON o.user_id=u.id " +
                     "WHERE u.email=? ORDER BY o.created_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar ocorrências do usuário", e); }
        return list;
    }

    @Override
    public List<GetOccurrenceDto> readAllByCity(String city) {
        List<GetOccurrenceDto> list = new ArrayList<>();
        String sql = SELECT_FIELDS +
                     "LEFT JOIN \"user\" u ON o.user_id=u.id " +
                     "WHERE o.city=? ORDER BY o.created_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, city);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { throw new RuntimeException("Erro ao listar ocorrências do município", e); }
        return list;
    }

    @Override
    public int countTodayByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM occurrence o " +
                     "JOIN \"user\" u ON o.user_id = u.id " +
                     "WHERE u.email = ? AND o.is_anonymous = false AND DATE(o.created_at) = CURRENT_DATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException("Erro ao contar ocorrências do dia", e); }
    }

    @Override
    public int countTodayAnonymousByIp(String ip) {
        String sql = "SELECT COUNT(*) FROM occurrence " +
                     "WHERE ip_address = ? AND is_anonymous = true AND DATE(created_at) = CURRENT_DATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new RuntimeException("Erro ao contar ocorrências anônimas do dia", e); }
    }

    private void restoreAutoCommit() {
        try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
    }

    private void rollback() { try { connection.rollback(); } catch (SQLException ignored) {} }
}
