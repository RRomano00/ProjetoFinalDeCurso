package br.com.faitec.falacidade.domain.dto.occurrence;

import java.time.LocalDateTime;

/** RN03/RF11: uma mudança de status da ocorrência (quem, quando, justificativa). */
public class OccurrenceHistoryDto {

    private String oldStatus;
    private String newStatus;
    private String observation;
    private String changedByName;   // exibido apenas para Funcionário/Administrador
    private LocalDateTime changedAt;

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String v) { this.oldStatus = v; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String v) { this.newStatus = v; }
    public String getObservation() { return observation; }
    public void setObservation(String v) { this.observation = v; }
    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String v) { this.changedByName = v; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime v) { this.changedAt = v; }
}
