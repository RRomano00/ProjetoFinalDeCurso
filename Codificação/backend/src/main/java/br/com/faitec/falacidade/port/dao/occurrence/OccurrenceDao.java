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

    List<GetOccurrenceDto> readGroup(int rootId);
}
