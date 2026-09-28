package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore.EMAIL;
import static br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore.SMS;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailMfaCodeStore – códigos por canal")
class EmailMfaCodeStoreTest {

    private static final String FONE = "+5535998761234";
    private final EmailMfaCodeStore store = new EmailMfaCodeStore();

    @Test
    @DisplayName("código do e-mail não vale no SMS")
    void canaisSeparados() {
        String c = store.generateCode(1);
        assertThat(store.validate(1, SMS, null, c)).isFalse();
        assertThat(store.validate(1, c)).isTrue();
    }

    @Test
    @DisplayName("código enviado a um número não vale para outro")
    void presoAoTelefone() {
        String c = store.generateCode(1, SMS, FONE);
        assertThat(store.validate(1, SMS, "+5535991112222", c)).isFalse();
        assertThat(store.validate(1, SMS, FONE, c)).isTrue();
    }

    @Test
    @DisplayName("sentWithin enxerga só o canal pedido")
    void intervaloPorCanal() {
        store.generateCode(1, SMS, FONE);
        assertThat(store.sentWithin(1, SMS, 60_000)).isTrue();
        assertThat(store.sentWithin(1, EMAIL, 60_000)).isFalse();
        assertThat(store.sentWithin(1, SMS, 0)).isFalse();
    }

    @Test
    @DisplayName("discard apaga o código e libera novo envio")
    void descarta() {
        String c = store.generateCode(1, SMS, FONE);
        store.discard(1, SMS);
        assertThat(store.sentWithin(1, SMS, 60_000)).isFalse();
        assertThat(store.validate(1, SMS, FONE, c)).isFalse();
    }
}
