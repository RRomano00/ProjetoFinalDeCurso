package br.com.faitec.falacidade.domain.dto.occurrence;

import br.com.faitec.falacidade.domain.Occurrence;
import jakarta.validation.constraints.NotNull;

public class UpdateOccurrenceStatusDto {
    @NotNull(message = "Status é obrigatório")
    private Occurrence.OccurrenceStatus newStatus;
    private String observation;
    private boolean collective;

    public Occurrence.OccurrenceStatus getNewStatus()          { return newStatus; }
    public void                    setNewStatus(Occurrence.OccurrenceStatus v){ this.newStatus = v; }
    public String                  getObservation()        { return observation; }
    public void                    setObservation(String v){ this.observation = v; }
    public boolean                 isCollective()          { return collective; }
    public void                    setCollective(boolean v){ this.collective = v; }
}
