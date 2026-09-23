package br.com.faitec.falacidade.controller;

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

    @Value("${app.mfa.exempt-emails:admin@falacidade.com}")
    private String mfaExemptEmails;

    public JwtAuthenticationRestController(
            AuthenticationService authenticationService, JwtService jwtService,
            UserDetailsService userDetailsService, MfaService mfaService,
            MfaTokenStore mfaTokenStore, UserService userService,
            EmailService emailService, EmailMfaCodeStore emailMfaCodeStore,
            ActiveSessionStore activeSessions) {
        this.authenticationService = authenticationService;
        this.jwtService            = jwtService;
        this.userDetailsService    = userDetailsService;
        this.mfaService            = mfaService;
        this.mfaTokenStore         = mfaTokenStore;
        this.userService           = userService;
        this.emailService          = emailService;
        this.emailMfaCodeStore     = emailMfaCodeStore;
        this.activeSessions        = activeSessions;
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

        if (mandatory && !app && !email) {
            log.info("MFA obrigatório por e-mail no primeiro acesso: " + user.getEmail());
            String token = mfaTokenStore.createToken(user.getId());
            sendEmailCode(user);
            return ResponseEntity.ok(LoginResponseDto.requiresMfa(token, false, true));
        }

        if (app || email) {
            String token = mfaTokenStore.createToken(user.getId());
            if (email && !app) sendEmailCode(user);
            return ResponseEntity.ok(LoginResponseDto.requiresMfa(token, app, email));
        }

        return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(user)));
    }

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
        if (byEmail && !user.isEmailMfaActive()) {
            mfaService.setEmailMfa(userId, true);
            log.info("2FA por e-mail ativado no primeiro acesso: " + user.getEmail());
        }
        return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(user)));
    }

    @GetMapping("/session")
    public ResponseEntity<Void> session() {
        return ResponseEntity.ok().build();
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
        String sessionId = activeSessions.open(user.getId());
        String jwt = jwtService.generateToken(ud, user.getFullname(), user.getRole(),
                                              user.getEmail(), user.getId(), sessionId);
        if (jwt == null || jwt.isEmpty())
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar token");
        return jwt;
    }
}
