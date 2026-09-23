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

    /** Desfaz o apoio do cidadão. Retorna false se ele não apoiava. */
    boolean unsupportOccurrence(int occurrenceId, int citizenId);

    int getSupportCount(int occurrenceId);
    boolean hasSupported(int occurrenceId, int citizenId);

    List<Integer> getSupportedOccurrenceIds(int citizenId);

    List<br.com.faitec.falacidade.domain.dto.occurrence.OccurrenceHistoryDto> getHistory(int occurrenceId);

    List<GetOccurrenceDto> getGroup(int occurrenceId);

    /**
     * Muda o status (individual ou de TODO o grupo), grava o histórico
     * e notifica por e-mail o(s) autor(es) identificado(s) com a mensagem opcional.
     */
    void changeStatus(int occurrenceId, String newStatus, int changedBy,
                      String message, boolean collective);

    /**
     * RF22: encaminha a ocorrência ao departamento responsável.
     *
     * Envia ao e-mail do departamento os dados do problema — sem nenhum dado
     * pessoal do autor — com as fotografias anexadas, move a ocorrência para
     * EM_ANDAMENTO e registra o trâmite no histórico. Se o e-mail não sair, o
     * estado não muda: não se registra um encaminhamento que não aconteceu.
     *
     * @return quais departamentos receberam a ocorrência e quais não receberam
     * @throws IllegalArgumentException ocorrência ou algum departamento inexistente
     */
    br.com.faitec.falacidade.domain.dto.occurrence.ForwardResultDto forwardToDepartment(
            int occurrenceId, java.util.List<Integer> departmentIds, int changedBy);
}
