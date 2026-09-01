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
    void updateStatus(int id, String newStatus, int changedBy, String observation);
    GetOccurrenceDto readByProtocolNumber(String protocolNumber);
    List<GetOccurrenceDto> findNearby(double lat, double lon, String type, double radiusMeters);
    GetOccurrenceDto findByAnonymousTrackingCodeHash(String codeHash);

    /** RF07: conta ocorrências identificadas do usuário no dia atual. */
    int countTodayByEmail(String email);

    /** RF08: conta ocorrências anônimas do IP no dia atual. Requer coluna ip_address na tabela. */
    int countTodayAnonymousByIp(String ip);

    /** Cidadão: lista apenas as ocorrências abertas pelo próprio usuário. */
    List<GetOccurrenceDto> readAllByUserEmail(String email);

    /** Funcionário/Administrador: lista apenas as ocorrências do município vinculado. */
    List<GetOccurrenceDto> readAllByCity(String city);

    /** RN03/RF11: histórico de mudanças de status (mais recente primeiro). */
    List<OccurrenceHistoryDto> readHistory(int occurrenceId);

    /** RF12: todas as ocorrências do grupo de duplicatas (raiz + encadeadas). */
    List<GetOccurrenceDto> readGroup(int rootId);
}
