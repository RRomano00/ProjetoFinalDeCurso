package br.com.faitec.falacidade.implementation.service.occurrence;

import br.com.faitec.falacidade.domain.Department;
import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceResponseDto;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.implementation.service.tracking.AnonymousTrackingCodeService;
import br.com.faitec.falacidade.port.dao.occurrence.OccurrenceDao;
import br.com.faitec.falacidade.port.dao.occurrence.OccurrenceSupportDao;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.department.DepartmentService;
import br.com.faitec.falacidade.port.service.occurrence.OccurrenceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OccurrenceServiceImpl implements OccurrenceService {

    private static final double DUPLICATE_RADIUS_METERS = 50.0;

    private final OccurrenceDao                occurrenceDao;
    private final OccurrenceSupportDao          supportDao;
    private final AnonymousTrackingCodeService  trackingCodeService;
    private final EmailService                  emailService;
    private final DepartmentService             departmentService;

    public OccurrenceServiceImpl(OccurrenceDao occurrenceDao,
                                OccurrenceSupportDao supportDao,
                                AnonymousTrackingCodeService trackingCodeService,
                                EmailService emailService,
                                DepartmentService departmentService) {
        this.occurrenceDao      = occurrenceDao;
        this.supportDao         = supportDao;
        this.trackingCodeService = trackingCodeService;
        this.emailService       = emailService;
        this.departmentService  = departmentService;
    }

    private static final int MAX_IDENTIFIED_PER_DAY = 5;
    private static final int MAX_ANONYMOUS_PER_DAY  = 3;

    @Override
    public CreateOccurrenceResponseDto createOccurrence(Occurrence entity, String clientIp) {
        if (entity == null) throw new IllegalArgumentException("Ocorrência não pode ser nula");
        if (isBlank(entity.getDescription())) throw new IllegalArgumentException("Descrição é obrigatória");
        if (isBlank(entity.getCity()))        throw new IllegalArgumentException("Cidade é obrigatória");

        if (entity.getPriority() == null && entity.getType() != null)
            entity.setPriority(Occurrence.Priority.fromType(entity.getType()));

        String plainCode = null;
        if (entity.isAnonymous()) {
            // RNF17: o endereço de rede é persistido e comparado apenas sob resumo
            // SHA-256 — o IP em texto claro não chega ao banco de dados.
            String ipHash = clientIp == null ? null : trackingCodeService.hash(clientIp);
            // RF08: limite de 3 ocorrências anônimas por IP por dia
            if (ipHash != null && occurrenceDao.countTodayAnonymousByIp(ipHash) >= MAX_ANONYMOUS_PER_DAY)
                throw new IllegalStateException("Limite de " + MAX_ANONYMOUS_PER_DAY + " ocorrências anônimas por dia atingido");
            entity.setIpAddress(ipHash);
            plainCode = trackingCodeService.generateCode();
            entity.setAnonymousTrackingCodeHash(trackingCodeService.hash(plainCode));
        } else {
            // RF07: limite de 5 ocorrências identificadas por usuário por dia
            if (!isBlank(entity.getEmail())
                    && occurrenceDao.countTodayByEmail(entity.getEmail()) >= MAX_IDENTIFIED_PER_DAY)
                throw new IllegalStateException("Limite de " + MAX_IDENTIFIED_PER_DAY + " ocorrências por dia atingido");
        }

        assignGroup(entity);

        int id = occurrenceDao.add(entity);
        if (id < 0) throw new RuntimeException("Falha ao persistir ocorrência");
        return new CreateOccurrenceResponseDto(id, entity.getProtocolNumber(), plainCode, entity.isAnonymous());
    }

    private void assignGroup(Occurrence entity) {
        if (entity.getLatitude() == null || entity.getLongitude() == null || entity.getType() == null) return;
        List<GetOccurrenceDto> nearby = occurrenceDao.findNearby(
            entity.getLatitude(), entity.getLongitude(), entity.getType().name(), DUPLICATE_RADIUS_METERS);
        if (nearby.isEmpty()) return;
        GetOccurrenceDto first = nearby.get(0);
        entity.setGroupId(first.getGroupId() != null ? first.getGroupId() : first.getId());
    }

    @Override
    public void updateOccurrenceStatusToInProgress(int id) {
        if (id >= 0) occurrenceDao.updateOccurrenceStatusToInProgress(id);
    }

    @Override
    public void updateOccurrenceStatusToConclude(int id) {
        if (id >= 0) occurrenceDao.updateOccurrenceStatusToConclude(id);
    }

    @Override
    public void updateStatus(int occurrenceId, String newStatus, int changedBy, String obs) {
        if (occurrenceId >= 0 && !isBlank(newStatus))
            occurrenceDao.updateStatus(occurrenceId, newStatus, changedBy, obs);
    }

    @Override
    public GetOccurrenceDto findById(int id) {
        return id >= 0 ? occurrenceDao.readById(id) : null;
    }

    @Override
    public List<GetOccurrenceDto> findAll() {
        return occurrenceDao.readall();
    }

    @Override
    public List<GetOccurrenceDto> findAllByUserEmail(String email) {
        return isBlank(email) ? List.of() : occurrenceDao.readAllByUserEmail(email);
    }

    @Override
    public List<GetOccurrenceDto> findAllByCity(String city) {
        return isBlank(city) ? List.of() : occurrenceDao.readAllByCity(city);
    }

    @Override
    public GetOccurrenceDto findByProtocolNumber(String protocol) {
        return isBlank(protocol) ? null : occurrenceDao.readByProtocolNumber(protocol);
    }

    @Override
    public GetOccurrenceDto findByAnonymousTrackingCode(String plainCode) {
        if (isBlank(plainCode)) return null;
        return occurrenceDao.findByAnonymousTrackingCodeHash(
            trackingCodeService.hash(plainCode.toUpperCase().trim()));
    }

    @Override
    public List<GetOccurrenceDto> findNearbyDuplicates(double lat, double lon, Occurrence.OccurrenceType type) {
        if (type == null) return List.of();
        return occurrenceDao.findNearby(lat, lon, type.name(), DUPLICATE_RADIUS_METERS);
    }

    @Override
    public boolean supportOccurrence(int occurrenceId, int citizenId) {
        if (occurrenceId < 0 || citizenId <= 0) return false;
        if (occurrenceDao.readById(occurrenceId) == null)
            throw new IllegalArgumentException("Ocorrência não encontrada");
        return supportDao.addSupport(occurrenceId, citizenId);
    }

    @Override
    public boolean unsupportOccurrence(int occurrenceId, int citizenId) {
        if (occurrenceId < 0 || citizenId <= 0) return false;
        if (occurrenceDao.readById(occurrenceId) == null)
            throw new IllegalArgumentException("Ocorrência não encontrada");
        return supportDao.removeSupport(occurrenceId, citizenId);
    }

    @Override
    public int getSupportCount(int occurrenceId) {
        return occurrenceId >= 0 ? supportDao.countByOccurrence(occurrenceId) : 0;
    }

    @Override
    public List<Integer> getSupportedOccurrenceIds(int citizenId) {
        return citizenId > 0 ? supportDao.findOccurrenceIdsByCitizen(citizenId) : List.of();
    }

    @Override
    public boolean hasSupported(int occurrenceId, int citizenId) {
        return occurrenceId >= 0 && citizenId > 0 && supportDao.hasSupported(occurrenceId, citizenId);
    }

    @Override
    public List<br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto> getHistory(int occurrenceId) {
        return occurrenceId >= 0 ? occurrenceDao.readHistory(occurrenceId) : List.of();
    }

    @Override
    public List<GetOccurrenceDto> getGroup(int occurrenceId) {
        GetOccurrenceDto o = findById(occurrenceId);
        if (o == null) return List.of();
        int root = o.getGroupId() != null ? o.getGroupId() : o.getId();
        return occurrenceDao.readGroup(root);
    }

    @Override
    public void changeStatus(int occurrenceId, String newStatus, int changedBy,
                             String message, boolean collective) {
        if (occurrenceId < 0 || isBlank(newStatus)) return;

        List<GetOccurrenceDto> targets = collective
            ? getGroup(occurrenceId)
            : List.of(findById(occurrenceId));

        for (GetOccurrenceDto o : targets) {
            if (o == null) continue;
            occurrenceDao.updateStatus(o.getId(), newStatus, changedBy, message);
            notifyAuthor(o, newStatus, message);
        }
    }

    /** RF22: texto fixo do trâmite, exigido no histórico da ocorrência. */
    private static final String FORWARD_NOTE = "Ocorrência encaminhada para departamento responsável";

    @Override
    public String forwardToDepartment(int occurrenceId, int departmentId, int changedBy) {
        GetOccurrenceDto occurrence = findById(occurrenceId);
        if (occurrence == null) throw new IllegalArgumentException("Ocorrência não encontrada");

        Department department = departmentService.findById(departmentId);
        if (department == null) throw new IllegalArgumentException("Departamento não encontrado");

        // O e-mail vem primeiro, e de forma síncrona: se ele falhar, a exceção sobe
        // e a ocorrência continua como estava. Registrar o encaminhamento de uma
        // mensagem que não saiu seria pior do que falhar.
        emailService.sendOccurrenceForwardEmail(department.getEmail(), department.getName(), occurrence);

        String note = FORWARD_NOTE + " — " + department.getName() + ".";
        occurrenceDao.updateStatus(occurrenceId, Occurrence.OccurrenceStatus.EM_ANDAMENTO.name(),
                                   changedBy, note, departmentId);
        notifyAuthor(occurrence, Occurrence.OccurrenceStatus.EM_ANDAMENTO.name(), note);
        return department.getName();
    }

    private void notifyAuthor(GetOccurrenceDto o, String newStatus, String message) {
        if (o.isAnonymous() || isBlank(o.getEmail())) return;
        try {
            emailService.sendStatusChangeEmail(o.getEmail(), o.getFullname(),
                o.getProtocolNumber(), newStatus, message);
        } catch (Exception ignored) { /* e-mail é melhor esforço */ }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
