package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.JwtAuthenticationRestController;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.AuthenticationDto;
import br.com.faitec.falacidade.domain.dto.auth.LoginResponseDto;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.implementation.service.authentication.ActiveSessionStore;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.JwtService;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.implementation.service.mfa.MfaTokenStore;
import br.com.faitec.falacidade.port.service.authentication.AuthenticationService;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthenticationRestController – 2FA obrigatório da equipe")
class JwtAuthenticationRestControllerMfaTest {

    @Mock AuthenticationService authenticationService;
    @Mock JwtService            jwtService;
    @Mock UserDetailsService    userDetailsService;
    @Mock MfaService            mfaService;
    @Mock UserService           userService;
    @Mock EmailService          emailService;

    JwtAuthenticationRestController sut;

    @BeforeEach
    void setUp() {
        sut = new JwtAuthenticationRestController(authenticationService, jwtService, userDetailsService,
            mfaService, new MfaTokenStore(), userService, emailService, new EmailMfaCodeStore(),
            new ActiveSessionStore());
        ReflectionTestUtils.setField(sut, "mfaExemptEmails", "admin@falacidade.com");
        when(userDetailsService.loadUserByUsername(anyString()))
            .thenReturn(new User("x", "y", List.of()));
        when(jwtService.generateToken(any(), any(), any(), anyString(), anyInt(), anyString())).thenReturn("jwt-fake");
    }

    private LoginResponseDto login(String email, UserModel.UserRole role) {
        UserModel user = new UserModel();
        user.setId(1); user.setEmail(email); user.setRole(role); user.setFullname("Fulano");
        when(authenticationService.authenticate(email, "senha")).thenReturn(user);

        AuthenticationDto dto = new AuthenticationDto();
        dto.setEmail(email); dto.setPassword("senha");
        ResponseEntity<LoginResponseDto> res = sut.authenticate(dto);
        return res.getBody();
    }

    @Test
    @DisplayName("funcionário sem 2FA configurado recebe o código por e-mail (não o QR Code)")
    void staffGetsEmailCodeOnFirstLogin() {
        LoginResponseDto r = login("func@prefeitura.com", UserModel.UserRole.EMPLOYEE);

        assertThat(r.getToken()).isNull();
        assertThat(r.isRequiresMfa()).isTrue();
        assertThat(r.isMfaEmailAvailable()).isTrue();
        assertThat(r.isMfaAppAvailable()).isFalse();
        verify(emailService).sendMfaCodeEmail(eq("func@prefeitura.com"), anyString());
    }

    @Test
    @DisplayName("conta dispensada entra direto, sem código")
    void exemptAccountSkipsMfa() {
        LoginResponseDto r = login("admin@falacidade.com", UserModel.UserRole.ADMINISTRATOR);

        assertThat(r.getToken()).isEqualTo("jwt-fake");
        assertThat(r.isRequiresMfa()).isFalse();
        verify(emailService, never()).sendMfaCodeEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("lista de dispensa vazia não dispensa ninguém")
    void emptyExemptionListExemptsNobody() {
        ReflectionTestUtils.setField(sut, "mfaExemptEmails", "");

        assertThat(login("admin@falacidade.com", UserModel.UserRole.ADMINISTRATOR).isRequiresMfa()).isTrue();
    }

    @Test
    @DisplayName("cidadão sem 2FA entra direto, como antes")
    void citizenWithoutMfaLogsInDirectly() {
        assertThat(login("cidadao@email.com", UserModel.UserRole.CITIZEN).getToken()).isEqualTo("jwt-fake");
    }

    private MfaVerifyDto code(String mfaToken, String totp) {
        MfaVerifyDto d = new MfaVerifyDto();
        d.setMfaToken(mfaToken); d.setTotpCode(totp); d.setMethod("EMAIL");
        return d;
    }

    private String lastEmailCode() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendMfaCodeEmail(anyString(), c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("código errado não derruba a etapa: dá para reenviar e entrar com o novo")
    void wrongCodeKeepsTheStepAlive() {
        String token = login("func@prefeitura.com", UserModel.UserRole.EMPLOYEE).getMfaToken();
        UserModel func = new UserModel();
        func.setId(1); func.setEmail("func@prefeitura.com"); func.setRole(UserModel.UserRole.EMPLOYEE);
        when(userService.findById(1)).thenReturn(func);

        assertThat(sut.verifyMfa(code(token, "000000")).getStatusCode().value()).isEqualTo(401);
        assertThat(sut.sendEmailMfaCode(code(token, null)).getStatusCode().value()).isEqualTo(200);
        assertThat(sut.verifyMfa(code(token, lastEmailCode())).getBody().getToken()).isEqualTo("jwt-fake");
    }

    @Test
    @DisplayName("cinco códigos errados encerram a etapa")
    void fiveWrongCodesEndTheStep() {
        String token = login("func@prefeitura.com", UserModel.UserRole.EMPLOYEE).getMfaToken();
        String certo = lastEmailCode();
        String errado = certo.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) sut.verifyMfa(code(token, errado));

        assertThat(sut.verifyMfa(code(token, certo)).getStatusCode().value()).isEqualTo(401);
    }
}
