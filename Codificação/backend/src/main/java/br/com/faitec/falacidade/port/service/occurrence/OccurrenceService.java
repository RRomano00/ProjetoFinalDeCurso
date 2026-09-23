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

    List<GetOccurrenceDto> findAllByUserEmail(String email);

    List<GetOccurrenceDto> findAllByCity(String city);

    boolean supportOccurrence(int occurrenceId, int citizenId);

    boolean unsupportOccurrence(int occurrenceId, int citizenId);

    int getSupportCount(int occurrenceId);
    boolean hasSupported(int occurrenceId, int citizenId);

    List<Integer> getSupportedOccurrenceIds(int citizenId);

    List<br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto> getHistory(int occurrenceId);

    List<GetOccurrenceDto> getGroup(int occurrenceId);

    void changeStatus(int occurrenceId, String newStatus, int changedBy,
                      String message, boolean collective);

    br.com.faitec.falacidade.domain.dto.occurrence.ForwardResultDto forwardToDepartment(
            int occurrenceId, java.util.List<Integer> departmentIds, int changedBy);
}
