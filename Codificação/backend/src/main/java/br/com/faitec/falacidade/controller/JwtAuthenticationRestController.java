package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.AuthenticationDto;
import br.com.faitec.falacidade.domain.dto.auth.LoginResponseDto;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.JwtService;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.implementation.service.mfa.MfaTokenStore;
import br.com.faitec.falacidade.port.service.authentication.AuthenticationService;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.logging.Logger;

@Profile("jwt")
@RestController
@RequestMapping("/api/authenticate")
public class JwtAuthenticationRestController {

    private static final Logger log = Logger.getLogger(JwtAuthenticationRestController.class.getName());

    private final AuthenticationService authenticationService;
    private final JwtService            jwtService;
    private final UserDetailsService    userDetailsService;
    private final MfaService            mfaService;
    private final MfaTokenStore         mfaTokenStore;
    private final UserService           userService;
    private final EmailService          emailService;
    private final EmailMfaCodeStore     emailMfaCodeStore;

    /**
     * Contas dispensadas do 2FA obrigatório de Funcionário/Administrador. A conta de
     * demonstração do seed usa um domínio que não recebe e-mail: exigir o código a
     * deixaria inacessível. Sobrescreva com app.mfa.exempt-emails=a@x.com,b@y.com
     * (vazio = ninguém dispensado). Quem está na lista ainda pode ativar o 2FA por
     * conta própria em Meu Perfil — a dispensa é só da obrigatoriedade.
     */
    @Value("${app.mfa.exempt-emails:admin@falacidade.com}")
    private String mfaExemptEmails;

    public JwtAuthenticationRestController(
            AuthenticationService authenticationService, JwtService jwtService,
            UserDetailsService userDetailsService, MfaService mfaService,
            MfaTokenStore mfaTokenStore, UserService userService,
            EmailService emailService, EmailMfaCodeStore emailMfaCodeStore) {
        this.authenticationService = authenticationService;
        this.jwtService            = jwtService;
        this.userDetailsService    = userDetailsService;
        this.mfaService            = mfaService;
        this.mfaTokenStore         = mfaTokenStore;
        this.userService           = userService;
        this.emailService          = emailService;
        this.emailMfaCodeStore     = emailMfaCodeStore;
    }

    @PostMapping
    public ResponseEntity<LoginResponseDto> authenticate(@RequestBody AuthenticationDto dto) {
        UserModel user;
        try {
            user = authenticationService.authenticate(dto.getEmail(), dto.getPassword());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean mandatory = (user.getRole() == UserModel.UserRole.EMPLOYEE
                          || user.getRole() == UserModel.UserRole.ADMINISTRATOR
                          || user.getRole() == UserModel.UserRole.SUPER_ADMIN)
                         && !isMfaExempt(user.getEmail());

        boolean app   = user.isAppMfaActive();
        boolean email = user.isEmailMfaActive();

        // Staff sem nenhum método configurado: o padrão é o 2FA por e-mail (o app
        // autenticador fica opcional, ativado depois em Meu Perfil). O e-mail é ativado
        // de fato quando o primeiro código for validado, em verifyMfa.
        if (mandatory && !app && !email) {
            log.info("MFA obrigatório por e-mail no primeiro acesso: " + user.getEmail());
            String token = mfaTokenStore.createToken(user.getId());
            sendEmailCode(user);
            return ResponseEntity.ok(LoginResponseDto.requiresMfa(token, false, true));
        }

        // Tem ao menos um método: pede o 2º fator
        if (app || email) {
            String token = mfaTokenStore.createToken(user.getId());
            // Se o único método for e-mail, já envia o código
            if (email && !app) sendEmailCode(user);
            return ResponseEntity.ok(LoginResponseDto.requiresMfa(token, app, email));
        }

        return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(user)));
    }

    /** Envia (ou reenvia) o código de verificação por e-mail para o token de login em andamento. */
    @PostMapping("/mfa/send-email")
    public ResponseEntity<Void> sendEmailMfaCode(@RequestBody MfaVerifyDto dto) {
        int userId = mfaTokenStore.peek(dto.getMfaToken());
        if (userId < 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UserModel user = userService.findById(userId);
        if (user == null || !user.isEmailMfaActive()) return ResponseEntity.badRequest().build();
        sendEmailCode(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mfa")
    public ResponseEntity<LoginResponseDto> verifyMfa(@Valid @RequestBody MfaVerifyDto dto) {
        int userId = mfaTokenStore.consume(dto.getMfaToken());
        if (userId < 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        boolean byEmail = "EMAIL".equalsIgnoreCase(dto.getMethod());
        boolean ok = byEmail
            ? emailMfaCodeStore.validate(userId, dto.getTotpCode())
            : mfaService.validateCode(userId, dto.getTotpCode());

        if (!ok) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        UserModel user = userService.findById(userId);
        // Primeiro acesso do staff: confirmar o código por e-mail é o que ativa o método.
        if (byEmail && !user.isEmailMfaActive()) {
            mfaService.setEmailMfa(userId, true);
            log.info("2FA por e-mail ativado no primeiro acesso: " + user.getEmail());
        }
        return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(user)));
    }

    private boolean isMfaExempt(String email) {
        return Arrays.stream(mfaExemptEmails.split(","))
            .map(String::trim).filter(e -> !e.isEmpty())
            .anyMatch(e -> e.equalsIgnoreCase(email));
    }

    private void sendEmailCode(UserModel user) {
        String code = emailMfaCodeStore.generateCode(user.getId());
        emailService.sendMfaCodeEmail(user.getEmail(), code);
    }

    private String generateJwt(UserModel user) {
        UserDetails ud = userDetailsService.loadUserByUsername(user.getEmail());
        String jwt = jwtService.generateToken(ud, user.getFullname(), user.getRole(), user.getEmail(), user.getId());
        if (jwt == null || jwt.isEmpty())
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar token");
        return jwt;
    }
}
