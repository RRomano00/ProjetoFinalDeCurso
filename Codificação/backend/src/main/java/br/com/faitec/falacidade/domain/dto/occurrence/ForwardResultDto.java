package br.com.faitec.falacidade.domain.dto.occurrence;

import java.util.List;

/**
 * RF22: o que aconteceu com cada destino do encaminhamento.
 *
 * Com vários departamentos não existe mais "deu certo" ou "deu errado" para o
 * conjunto: um e-mail que saiu não pode ser desfeito porque o seguinte falhou.
 * Então o resultado diz, nome a nome, quem recebeu e quem não recebeu.
 */
public record ForwardResultDto(List<String> enviados, List<String> falharam) {

    public boolean nenhumEnviado() { return enviados.isEmpty(); }
}
