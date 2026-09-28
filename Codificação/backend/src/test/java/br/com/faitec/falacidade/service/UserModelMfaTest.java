package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UserModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserModel – métodos de 2FA ativos")
class UserModelMfaTest {

    @Test
    @DisplayName("conta app, e-mail e SMS")
    void contaOsTres() {
        UserModel u = new UserModel();
        assertThat(u.activeMfaCount()).isZero();
        assertThat(u.isSmsMfaActive()).isFalse();

        u.setMfaSmsPhone("+5535998761234");
        u.setMfaEmailEnabled(true);
        u.setMfaSetupDone(true);

        assertThat(u.isSmsMfaActive()).isTrue();
        assertThat(u.activeMfaCount()).isEqualTo(3);
    }
}
