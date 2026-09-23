package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.service.authentication.ActiveSessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ActiveSessionStore — uma conta, uma sessão")
class ActiveSessionStoreTest {

    private final ActiveSessionStore sut = new ActiveSessionStore();

    @Test
    @DisplayName("o login mais recente derruba o anterior da mesma conta")
    void newLoginSupersedesPrevious() {
        String primeiro = sut.open(7);
        String segundo  = sut.open(7);

        assertThat(sut.superseded(7, primeiro)).isTrue();
        assertThat(sut.superseded(7, segundo)).isFalse();
    }

    @Test
    @DisplayName("o login de uma conta não derruba a sessão de outra")
    void otherAccountIsUntouched() {
        String daMaria = sut.open(1);
        sut.open(2);

        assertThat(sut.superseded(1, daMaria)).isFalse();
    }

    @Test
    @DisplayName("sem login registrado (servidor recém-iniciado) ninguém é derrubado")
    void unknownAccountIsNotSuperseded() {
        assertThat(sut.superseded(99, "sessao-de-antes-do-restart")).isFalse();
        assertThat(sut.superseded(null, null)).isFalse();
    }
}
