package br.com.faitec.falacidade.domain.dto.occurrence;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ForwardOccurrenceDto {

    @NotEmpty(message = "Selecione ao menos um departamento")
    private List<Integer> departmentIds;

    public List<Integer> getDepartmentIds()            { return departmentIds; }
    public void setDepartmentIds(List<Integer> v)      { this.departmentIds = v; }
}
