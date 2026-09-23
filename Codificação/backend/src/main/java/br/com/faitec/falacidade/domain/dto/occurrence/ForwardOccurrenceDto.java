package br.com.faitec.falacidade.domain.dto.occurrence;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * RF22: payload do encaminhamento — POST /api/occurrence/{id}/forward.
 *
 * A mesma ocorrência pode interessar a mais de um setor: um buraco que expõe
 * fiação é de Obras e de Iluminação. Por isso a lista, e não um único destino.
 */
public class ForwardOccurrenceDto {

    @NotEmpty(message = "Selecione ao menos um departamento")
    private List<Integer> departmentIds;

    public List<Integer> getDepartmentIds()            { return departmentIds; }
    public void setDepartmentIds(List<Integer> v)      { this.departmentIds = v; }
}
