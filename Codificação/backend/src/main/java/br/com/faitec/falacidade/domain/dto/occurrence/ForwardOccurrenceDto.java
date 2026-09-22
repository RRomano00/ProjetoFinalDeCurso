package br.com.faitec.falacidade.domain.dto.occurrence;

import jakarta.validation.constraints.Positive;

/** RF22: payload do encaminhamento — POST /api/occurrence/{id}/forward. */
public class ForwardOccurrenceDto {

    @Positive(message = "Selecione o departamento")
    private int departmentId;

    public int  getDepartmentId()          { return departmentId; }
    public void setDepartmentId(int v)     { this.departmentId = v; }
}
