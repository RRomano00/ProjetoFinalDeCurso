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

    /**
     * "Exclui" a ocorrência: status FINALIZADA (definitivo), fotos removidas do banco e autor
     * avisado. Devolve as imagens, para quem chamou apagá-las no Cloudinary.
     */
    List<String> finalizeOccurrence(int id, int changedBy);

    void changeStatus(int occurrenceId, String newStatus, int changedBy,
                      String message, boolean collective);

    br.com.faitec.falacidade.domain.dto.occurrence.ForwardResultDto forwardToDepartment(
            int occurrenceId, java.util.List<Integer> departmentIds, int changedBy,
            String appUrl, java.util.List<String> contactEmails);

    /** Departamento pede a conclusão (foto do serviço): entra no histórico, só a equipe vê. */
    void requestCompletion(int occurrenceId, int departmentId, String message, String attachmentUrl,
                           String attachmentPublicId);
}
