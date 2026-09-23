package br.com.faitec.falacidade.domain.dto.occurrence;

import java.util.List;

public record ForwardResultDto(List<String> enviados, List<String> falharam) {

    public boolean nenhumEnviado() { return enviados.isEmpty(); }
}
