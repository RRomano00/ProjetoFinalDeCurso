package br.com.faitec.falacidade.port.dao.occurrence;

public interface OccurrenceSupportDao {

    boolean addSupport(int occurrenceId, int citizenId);

    int countByOccurrence(int occurrenceId);

    boolean hasSupported(int occurrenceId, int citizenId);

    java.util.List<Integer> findOccurrenceIdsByCitizen(int citizenId);
}
