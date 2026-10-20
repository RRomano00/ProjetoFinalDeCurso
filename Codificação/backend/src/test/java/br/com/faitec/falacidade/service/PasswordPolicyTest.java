package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordPolicy — regra única de senha")
class PasswordPolicyTest {

    // O front (shared/br-mask.ts) aceita exatamente os mesmos casos.
    @ParameterizedTest
    @ValueSource(strings = {"Senha@123", "Senha_123", "Senha 123", "Éssenha1!"})
    @DisplayName("aceita letra + número + qualquer símbolo, com 8 ou mais caracteres")
    void accepts(String password) {
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"senha123", "Senha@ab", "1234567@", "Se@1", ""})
    @DisplayName("recusa sem símbolo, sem número, sem letra, curta ou vazia")
    void rejects(String password) {
        assertThat(PasswordPolicy.isValid(password)).isFalse();
    }
}
