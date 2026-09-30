package br.com.faitec.falacidade.domain.dto.occurrence;

import java.time.LocalDateTime;
import java.util.List;

/** O que a página do departamento (link do encaminhamento) recebe: sem dados pessoais. */
public class DepartmentOccurrenceViewDto {

    private String departmentName;
    private GetOccurrenceDto occurrence;
    private List<GetOccurrenceDto> group;
    private List<OccurrenceHistoryDto> history;
    private boolean canRequestCompletion;
    private LocalDateTime pendingRequestAt;

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String v) { this.departmentName = v; }
    public GetOccurrenceDto getOccurrence() { return occurrence; }
    public void setOccurrence(GetOccurrenceDto v) { this.occurrence = v; }
    public List<GetOccurrenceDto> getGroup() { return group; }
    public void setGroup(List<GetOccurrenceDto> v) { this.group = v; }
    public List<OccurrenceHistoryDto> getHistory() { return history; }
    public void setHistory(List<OccurrenceHistoryDto> v) { this.history = v; }
    public boolean isCanRequestCompletion() { return canRequestCompletion; }
    public void setCanRequestCompletion(boolean v) { this.canRequestCompletion = v; }
    public LocalDateTime getPendingRequestAt() { return pendingRequestAt; }
    public void setPendingRequestAt(LocalDateTime v) { this.pendingRequestAt = v; }
}
