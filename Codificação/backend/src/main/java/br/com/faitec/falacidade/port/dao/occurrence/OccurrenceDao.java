package br.com.faitec.falacidade.port.dao.occurrence;

import br.com.faitec.falacidade.domain.Occurrence;
import br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto;
import br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto;
import br.com.faitec.falacidade.port.dao.crud.CreateDao;
import br.com.faitec.falacidade.port.dao.crud.ReadDao;
import java.util.List;

public interface OccurrenceDao extends CreateDao<Occurrence>, ReadDao<GetOccurrenceDto> {

    void updateOccurrenceStatusToInProgress(int id);
    void updateOccurrenceStatusToConclude(int id);
    default void updateStatus(int id, String newStatus, int changedBy, String observation) {
        updateStatus(id, newStatus, changedBy, observation, null);
    }

    void updateStatus(int id, String newStatus, int changedBy, String observation, Integer departmentId);
    GetOccurrenceDto readByProtocolNumber(String protocolNumber);
    List<GetOccurrenceDto> findNearby(double lat, double lon, String type, double radiusMeters);
    GetOccurrenceDto findByAnonymousTrackingCodeHash(String codeHash);

    int countTodayByEmail(String email);

    int countTodayAnonymousByIp(String ip);

    List<GetOccurrenceDto> readAllByUserEmail(String email);

    List<GetOccurrenceDto> readAllByCity(String city);

    List<OccurrenceHistoryDto> readHistory(int occurrenceId);

    /** Solicitação de conclusão do departamento: entra no histórico sem mudar o status. */
    void insertCompletionRequest(int occurrenceId, int departmentId, String message, String attachmentUrl);

    List<GetOccurrenceDto> readGroup(int rootId);

    /** Imagens da ocorrência no Cloudinary (a principal e as adicionais). */
    List<String> readMediaPublicIds(int id);

    /** Apaga do banco o registro das fotos (a principal e as adicionais) da ocorrência. */
    void clearMedia(int id);
}
