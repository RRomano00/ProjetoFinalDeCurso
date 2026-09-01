package br.com.faitec.falacidade.port.dao.occurrence;

/**
 * RF16: apoio ("Apoiar") de cidadãos a ocorrências existentes.
 * Cada cidadão pode apoiar uma mesma ocorrência apenas uma vez
 * (UNIQUE occurrence_id + citizen_id na tabela occurrence_support).
 */
public interface OccurrenceSupportDao {

    /** Registra o apoio. Retorna false se o cidadão já apoiava essa ocorrência. */
    boolean addSupport(int occurrenceId, int citizenId);

    /** Total de apoios da ocorrência. */
    int countByOccurrence(int occurrenceId);

    /** Se o cidadão já apoiou essa ocorrência. */
    boolean hasSupported(int occurrenceId, int citizenId);
}
