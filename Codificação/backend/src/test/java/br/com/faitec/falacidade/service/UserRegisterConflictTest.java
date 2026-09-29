package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.controller.UserRestController;
import br.com.faitec.falacidade.domain.DuplicateFieldException;
import br.com.faitec.falacidade.domain.dto.user.RegisterUserDto;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRestController – cadastro com dado já usado por outra conta")
class UserRegisterConflictTest {

    @Mock UserService          userService;
    @Mock PasswordResetService passwordResetService;
    @Mock EmailService         emailService;
    @Mock MfaService           mfaService;
    @Mock SmsCodeSender        smsCodeSender;

    UserRestController sut;

    @BeforeEach
    void setUp() {
        sut = new UserRestController(userService, passwordResetService, emailService,
                                     mfaService, new EmailMfaCodeStore(), smsCodeSender);
    }

    @Test
    @DisplayName("o 409 diz qual campo repetiu, para a tela voltar à etapa certa")
    void conflictNamesTheField() {
        when(userService.create(any()))
            .thenThrow(new DuplicateFieldException("phoneNumber", "Este celular já está cadastrado em outra conta."));
        RegisterUserDto dto = new RegisterUserDto();
        dto.setEmail("novo@email.com");

        ResponseEntity<?> r = sut.register(dto);

        assertThat(r.getStatusCode().value()).isEqualTo(409);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) r.getBody();
        assertThat(body)
            .containsEntry("field", "phoneNumber")
            .containsEntry("error", "Este celular já está cadastrado em outra conta.");
    }
}
