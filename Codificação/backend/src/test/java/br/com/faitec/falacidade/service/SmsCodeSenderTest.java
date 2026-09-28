package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.implementation.service.mfa.SmsCodeSender;
import br.com.faitec.falacidade.port.service.sms.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore.SMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmsCodeSender – código por SMS, com intervalo mínimo")
class SmsCodeSenderTest {

    private static final String FONE = "+5535998761234";

    @Mock SmsService sms;
    EmailMfaCodeStore codes;
    SmsCodeSender sut;

    @BeforeEach
    void setUp() {
        codes = new EmailMfaCodeStore();
        sut = new SmsCodeSender(sms, codes);
        when(sms.isEnabled()).thenReturn(true);
    }

    private String textoEnviado() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(sms, atLeastOnce()).send(eq(FONE), c.capture());
        return c.getValue();
    }

    private String codigoDo(String texto) {
        return texto.replaceAll("\\D*(\\d{6}).*", "$1");
    }

    @Test
    @DisplayName("envia e o código vale para aquele número")
    void enviaEValida() {
        assertThat(sut.send(1, FONE)).isEqualTo(HttpStatus.OK);
        assertThat(codes.validate(1, SMS, FONE, codigoDo(textoEnviado()))).isTrue();
    }

    @Test
    @DisplayName("o texto cabe em um SMS: sem acento e com até 160 caracteres")
    void textoCabeEmUmSms() {
        sut.send(1, FONE);
        String texto = textoEnviado();
        assertThat(texto).startsWith("FalaCidade");
        assertThat(texto.length()).isLessThanOrEqualTo(160);
        assertThat(StandardCharsets.US_ASCII.newEncoder().canEncode(texto)).isTrue();
    }

    @Test
    @DisplayName("desligado não envia nada")
    void desligado() {
        when(sms.isEnabled()).thenReturn(false);
        assertThat(sut.send(1, FONE)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("reenvio antes de um minuto é barrado e o código anterior continua valendo")
    void intervaloMinimo() {
        assertThat(sut.send(1, FONE)).isEqualTo(HttpStatus.OK);
        String primeiro = codigoDo(textoEnviado());
        assertThat(sut.send(1, FONE)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(sms, times(1)).send(anyString(), anyString());
        assertThat(codes.validate(1, SMS, FONE, primeiro)).isTrue();
    }

    @Test
    @DisplayName("falha do gateway libera nova tentativa na hora")
    void falhaDoGatewayLiberaNovaTentativa() {
        doThrow(new IllegalStateException("fora do ar")).when(sms).send(anyString(), anyString());
        assertThat(sut.send(1, FONE)).isEqualTo(HttpStatus.BAD_GATEWAY);

        doNothing().when(sms).send(anyString(), anyString());
        assertThat(sut.send(1, FONE)).isEqualTo(HttpStatus.OK);
    }
}
