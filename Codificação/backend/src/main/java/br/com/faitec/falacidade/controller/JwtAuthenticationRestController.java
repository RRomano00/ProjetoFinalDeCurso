package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.AuthenticationDto;
import br.com.faitec.falacidade.domain.dto.auth.LoginResponseDto;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.implementation.service.authentication.ActiveSessionStore;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.JwtService;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.implementation.service.mfa.MfaTokenStore;
import br.com.faitec.falacidade.implementation.service.mfa.SmsCodeSender;
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
    private final ActiveSessionStore    activeSessions;
    private final SmsCodeSender         smsSender;

    @Value("${app.mfa.exempt-emails:admin@falacidade.com}")
    private String mfaExemptEmails;

    public JwtAuthenticationRestController(
            AuthenticationService authenticationService, JwtService jwtService,
            UserDetailsService userDetailsService, MfaService mfaService,
            MfaTokenStore mfaTokenStore, UserService userService,
            EmailService emailService, EmailMfaCodeStore emailMfaCodeStore,
            ActiveSessionStore activeSessions, SmsCodeSender smsSender) {
        this.authenticationService = authenticationService;
        this.jwtService            = jwtService;
        this.userDetailsService    = userDetailsService;
        this.mfaService            = mfaService;
        this.mfaTokenStore         = mfaTokenStore;
        this.userService           = userService;
        this.emailService          = emailService;
        this.emailMfaCodeStore     = emailMfaCodeStore;
        this.activeSessions        = activeSessions;
        this.smsSender             = smsSender;
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

        boolean app   = user.isAppMfaActive();
        boolean sms   = user.isSmsMfaActive() && smsSender.isEnabled();
        boolean email = emailCodeAllowed(user);

        if (!app && !email && !sms)
            return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(user)));

        String token = mfaTokenStore.createToken(user.getId());
        // Um método só dispensa a tela de escolha: o e-mail já sai junto do login.
        // O SMS não: a tela o pede em /mfa/send-sms e assim fica sabendo se o
        // celular gateway falhou, em vez de esperar um código que não vem.
        if (email && !app && !sms) sendEmailCode(user);
        return ResponseEntity.ok(LoginResponseDto.requiresMfa(token, app, email, sms));
    }

    @PostMapping("/mfa/send-email")
    public ResponseEntity<Void> sendEmailMfaCode(@RequestBody MfaVerifyDto dto) {
        int userId = mfaTokenStore.peek(dto.getMfaToken());
        if (userId < 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UserModel user = userService.findById(userId);
        if (user == null || !emailCodeAllowed(user)) return ResponseEntity.badRequest().build();
        sendEmailCode(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mfa/send-sms")
    public ResponseEntity<Void> sendSmsMfaCode(@RequestBody MfaVerifyDto dto) {
        int userId = mfaTokenStore.peek(dto.getMfaToken());
        if (userId < 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UserModel user = userService.findById(userId);
        if (user == null || !user.isSmsMfaActive()) return ResponseEntity.badRequest().build();
        return ResponseEntity.status(smsSender.send(userId, user.getMfaSmsPhone())).build();
    }

    @PostMapping("/mfa")
    public ResponseEntity<LoginResponseDto> verifyMfa(@Valid @RequestBody MfaVerifyDto dto) {
        int userId = mfaTokenStore.peek(dto.getMfaToken());
        if (userId < 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String method = dto.getMethod() == null ? "APP" : dto.getMethod().toUpperCase();
        boolean ok = switch (method) {
            case "EMAIL" -> emailMfaCodeStore.validate(userId, dto.getTotpCode());
            case "SMS"   -> validSmsCode(userId, dto.getTotpCode());
            default      -> mfaService.validateCode(userId, dto.getTotpCode());
        };

        if (!ok) {
            mfaTokenStore.fail(dto.getMfaToken());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        mfaTokenStore.consume(dto.getMfaToken());

        UserModel user = userService.findById(userId);
        if ("EMAIL".equals(method) && !user.isEmailMfaActive() && isMfaMandatory(user)) {
            mfaService.setEmailMfa(userId, true);
            log.info("2FA por e-mail ativado no primeiro acesso: " + user.getEmail());
        }
        return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(user)));
    }

    @GetMapping("/session")
    public ResponseEntity<Void> session() {
        return ResponseEntity.ok().build();
    }

    private boolean isMfaMandatory(UserModel user) {
        return user.isStaff() && !isMfaExempt(user.getEmail());
    }

    private boolean isMfaExempt(String email) {
        return Arrays.stream(mfaExemptEmails.split(","))
            .map(String::trim).filter(e -> !e.isEmpty())
            .anyMatch(e -> e.equalsIgnoreCase(email));
    }

    /**
     * E-mail vale quando está ativo, no primeiro acesso da equipe e como reserva
     * de quem só tem SMS com o envio desligado — sem ela a conta ficaria sem
     * como entrar ou entraria sem segundo fator.
     */
    private boolean emailCodeAllowed(UserModel user) {
        if (user.isEmailMfaActive()) return true;
        if (user.isAppMfaActive()) return false;
        if (user.isSmsMfaActive()) return !smsSender.isEnabled();
        return isMfaMandatory(user);
    }

    private boolean validSmsCode(int userId, String code) {
        UserModel user = userService.findById(userId);
        return user != null && user.isSmsMfaActive()
            && emailMfaCodeStore.validate(userId, EmailMfaCodeStore.SMS, user.getMfaSmsPhone(), code);
    }

    private void sendEmailCode(UserModel user) {
        String code = emailMfaCodeStore.generateCode(user.getId());
        emailService.sendMfaCodeEmail(user.getEmail(), code);
    }

    private String generateJwt(UserModel user) {
        UserDetails ud = userDetailsService.loadUserByUsername(user.getEmail());
        String sessionId = activeSessions.open(user.getId());
        String jwt = jwtService.generateToken(ud, user.getFullname(), user.getRole(),
                                              user.getEmail(), user.getId(), sessionId);
        if (jwt == null || jwt.isEmpty())
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar token");
        return jwt;
    }
}
