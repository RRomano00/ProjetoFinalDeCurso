package br.com.faitec.falacidade.domain.dto.occurrence;

import java.time.LocalDateTime;

public class OccurrenceHistoryDto {

    public static final String KIND_STATUS = "STATUS";
    public static final String KIND_COMPLETION_REQUEST = "COMPLETION_REQUEST";

    private String oldStatus;
    private String newStatus;
    private String observation;
    private String changedByName;
    private LocalDateTime changedAt;
    private String  kind = KIND_STATUS;
    private String  attachmentUrl;
    private String  departmentName;
    private Integer departmentId;

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
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v == null ? KIND_STATUS : v; }
    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String v) { this.attachmentUrl = v; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String v) { this.departmentName = v; }
    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer v) { this.departmentId = v; }
    public boolean isCompletionRequest() { return KIND_COMPLETION_REQUEST.equals(kind); }
}
