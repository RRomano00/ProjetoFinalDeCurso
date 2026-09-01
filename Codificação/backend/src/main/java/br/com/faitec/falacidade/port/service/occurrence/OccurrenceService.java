package br.com.faitec.falacidade.port.service.occurrence;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.dto.occurrence.CreateOccurrenceResponseDto;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.port.service.crud.ReadService;

import java.util.List;

public interface OccurrenceService extends ReadService<GetOccurrenceDto> {

    CreateOccurrenceResponseDto createOccurrence(Occurrence entity, String clientIp);
    void updateOccurrenceStatusToInProgress(int id);
    void updateOccurrenceStatusToConclude(int id);
    void updateStatus(int occurrenceId, String newStatus, int changedByUserId, String observation);
    GetOccurrenceDto findByProtocolNumber(String protocolNumber);
    List<GetOccurrenceDto> findNearbyDuplicates(double lat, double lon, Occurrence.OccurrenceType type);
    GetOccurrenceDto findByAnonymousTrackingCode(String plainCode);

    /** Cidadão: lista apenas as ocorrências abertas pelo próprio usuário. */
    List<GetOccurrenceDto> findAllByUserEmail(String email);

    /** Funcionário/Administrador: lista apenas as ocorrências do município vinculado. */
    List<GetOccurrenceDto> findAllByCity(String city);

    /** RF16: registra o apoio do cidadão à ocorrência. Retorna false se já apoiava. */
    boolean supportOccurrence(int occurrenceId, int citizenId);

    /** RF16: total de apoios da ocorrência. */
    int getSupportCount(int occurrenceId);

    /** RF16: se o cidadão já apoiou a ocorrência. */
    boolean hasSupported(int occurrenceId, int citizenId);

    /** RN03/RF11: histórico de mudanças de status da ocorrência. */
    List<br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto> getHistory(int occurrenceId);

    /** RF12: ocorrências do mesmo grupo de duplicatas (raiz + encadeadas). */
    List<GetOccurrenceDto> getGroup(int occurrenceId);

    /**
     * RF12/RN03: muda o status (individual ou de TODO o grupo), grava o histórico
     * e notifica por e-mail o(s) autor(es) identificado(s) com a mensagem opcional.
     */
    void changeStatus(int occurrenceId, String newStatus, int changedBy,
                      String message, boolean collective);
}
