package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.UserRestController;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.implementation.service.mfa.SmsCodeSender;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.password.PasswordResetService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserRestController – exclusão da conta com 2FA por SMS")
class UserAccountDeletionSmsTest {

    private static final String FONE = "+5535998761234";

    @Mock UserService          userService;
    @Mock PasswordResetService passwordResetService;
    @Mock EmailService         emailService;
    @Mock MfaService           mfaService;
    @Mock SmsCodeSender        smsCodeSender;

    EmailMfaCodeStore  codes;
    UserRestController sut;
    final Authentication auth = new UsernamePasswordAuthenticationToken("sms@email.com", null, List.of());

    @BeforeEach
    void setUp() {
        codes = new EmailMfaCodeStore();
        sut = new UserRestController(userService, passwordResetService, emailService,
                                     mfaService, codes, smsCodeSender);
        UserModel u = new UserModel();
        u.setId(3); u.setEmail("sms@email.com"); u.setRole(UserModel.UserRole.CITIZEN);
        u.setMfaSmsPhone(FONE);
        when(userService.findByEmail("sms@email.com")).thenReturn(u);
        when(smsCodeSender.isEnabled()).thenReturn(true);
    }

    private MfaVerifyDto codigo(String c) {
        MfaVerifyDto d = new MfaVerifyDto();
        d.setTotpCode(c);
        return d;
    }

    @Test
    @DisplayName("quem só tem SMS recebe o código de exclusão por SMS")
    void codigoVaiPorSms() {
        when(smsCodeSender.send(3, FONE)).thenReturn(HttpStatus.OK);

        assertThat(sut.sendAccountDeletionCode(auth).getStatusCode().value()).isEqualTo(200);
        verify(smsCodeSender).send(3, FONE);
        verify(emailService, never()).sendMfaCodeEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("o código do SMS confirma a exclusão")
    void codigoDoSmsExclui() {
        String c = codes.generateCode(3, EmailMfaCodeStore.SMS, FONE);

        assertThat(sut.deleteOwnAccount(codigo(c), auth).getStatusCode().value()).isEqualTo(204);
        verify(userService).delete(3);
    }

    @Test
    @DisplayName("sem código válido a conta não é excluída")
    void semCodigoNaoExclui() {
        assertThat(sut.deleteOwnAccount(codigo("123456"), auth).getStatusCode().value()).isEqualTo(401);
        verify(userService, never()).delete(anyInt());
    }

    @Test
    @DisplayName("com o SMS desligado, o código de exclusão vai por e-mail e confirma a exclusão")
    void smsDesligadoUsaEmail() {
        when(smsCodeSender.isEnabled()).thenReturn(false);

        assertThat(sut.sendAccountDeletionCode(auth).getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendMfaCodeEmail(eq("sms@email.com"), c.capture());
        verify(smsCodeSender, never()).send(anyInt(), anyString());

        assertThat(sut.deleteOwnAccount(codigo(c.getValue()), auth).getStatusCode().value()).isEqualTo(204);
        verify(userService).delete(3);
    }
}
