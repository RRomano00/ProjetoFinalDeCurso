package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.MfaRestController;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.implementation.service.mfa.SmsCodeSender;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.sms.SmsService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MfaRestController – 2FA por SMS no perfil")
class MfaRestControllerSmsTest {

    private static final String FONE = "+5535998761234";

    @Mock MfaService   mfaService;
    @Mock UserService  userService;
    @Mock EmailService emailService;
    @Mock SmsService   smsService;

    EmailMfaCodeStore codes;
    MfaRestController sut;
    UserModel user;
    final Authentication auth = new UsernamePasswordAuthenticationToken("sms@email.com", null, List.of());

    @BeforeEach
    void setUp() {
        codes = new EmailMfaCodeStore();
        sut = new MfaRestController(mfaService, userService, emailService, codes,
                                    new SmsCodeSender(smsService, codes));
        when(smsService.isEnabled()).thenReturn(true);
        user = new UserModel();
        user.setId(1); user.setEmail("sms@email.com"); user.setRole(UserModel.UserRole.CITIZEN);
        when(userService.findByEmail("sms@email.com")).thenReturn(user);
    }

    private MfaVerifyDto dto(String phone, String code) {
        MfaVerifyDto d = new MfaVerifyDto();
        d.setPhone(phone); d.setTotpCode(code);
        return d;
    }

    private String enviado() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(smsService, atLeastOnce()).send(eq(FONE), c.capture());
        return c.getValue().replaceAll("\\D*(\\d{6}).*", "$1");
    }

    @Test
    @DisplayName("ativa com o código enviado ao número informado")
    void ativa() {
        assertThat(sut.sendSmsEnableCode(dto("(35) 99876-1234", null), auth).getStatusCode().value()).isEqualTo(200);
        assertThat(sut.enableSms(dto("35 99876-1234", enviado()), auth).getStatusCode().value()).isEqualTo(200);
        verify(mfaService).setSmsMfa(1, FONE);
    }

    @Test
    @DisplayName("código enviado a um número não ativa outro")
    void codigoDeOutroNumeroNaoAtiva() {
        sut.sendSmsEnableCode(dto("(35) 99876-1234", null), auth);
        assertThat(sut.enableSms(dto("(35) 99111-2222", enviado()), auth).getStatusCode().value()).isEqualTo(401);
        verify(mfaService, never()).setSmsMfa(anyInt(), any());
    }

    @Test
    @DisplayName("fixo ou número inválido é recusado sem enviar SMS")
    void numeroInvalido() {
        assertThat(sut.sendSmsEnableCode(dto("(35) 3471-1234", null), auth).getStatusCode().value()).isEqualTo(400);
        assertThat(sut.sendSmsEnableCode(dto("1234", null), auth).getStatusCode().value()).isEqualTo(400);
        verify(smsService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("equipe com só o SMS não pode desligá-lo")
    void equipeMantemUmMetodo() {
        user.setRole(UserModel.UserRole.EMPLOYEE);
        user.setMfaSmsPhone(FONE);
        assertThat(sut.disableSms(dto(null, "123456"), auth).getStatusCode().value()).isEqualTo(409);
        verify(mfaService, never()).setSmsMfa(anyInt(), any());
    }

    @Test
    @DisplayName("equipe com e-mail e SMS pode desligar o e-mail")
    void equipeComDoisMetodosDesligaUm() {
        user.setRole(UserModel.UserRole.EMPLOYEE);
        user.setMfaEmailEnabled(true);
        user.setMfaSmsPhone(FONE);
        String code = codes.generateCode(1);
        assertThat(sut.disableEmail(dto(null, code), auth).getStatusCode().value()).isEqualTo(200);
        verify(mfaService).setEmailMfa(1, false);
    }

    @Test
    @DisplayName("desativa com o código enviado ao celular confirmado")
    void desativa() {
        user.setMfaSmsPhone(FONE);
        assertThat(sut.sendSmsDisableCode(auth).getStatusCode().value()).isEqualTo(200);
        assertThat(sut.disableSms(dto(null, enviado()), auth).getStatusCode().value()).isEqualTo(200);
        verify(mfaService).setSmsMfa(1, null);
    }

    @Test
    @DisplayName("celular de outra conta não recebe SMS nem é ativado")
    void celularDeOutraConta() {
        when(userService.isPhoneInUse(1, FONE)).thenReturn(true);

        assertThat(sut.sendSmsEnableCode(dto("(35) 99876-1234", null), auth).getStatusCode().value()).isEqualTo(409);
        verify(smsService, never()).send(anyString(), anyString());

        String c = codes.generateCode(1, EmailMfaCodeStore.SMS, FONE);
        assertThat(sut.enableSms(dto("(35) 99876-1234", c), auth).getStatusCode().value()).isEqualTo(409);
        verify(mfaService, never()).setSmsMfa(anyInt(), any());
    }
}
