package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.AuthenticationDto;
import br.com.faitec.falacidade.domain.dto.auth.LoginResponseDto;
import br.com.faitec.falacidade.domain.dto.auth.MfaSetupResponseDto;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.JwtService;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.implementation.service.mfa.MfaTokenStore;
import br.com.faitec.falacidade.port.service.authentication.AuthenticationService;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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

        boolean mandatory = user.getRole() == UserModel.UserRole.EMPLOYEE
                         || user.getRole() == UserModel.UserRole.ADMINISTRATOR;

        boolean app   = user.isAppMfaActive();
        boolean email = user.isEmailMfaActive();

        // Staff sem nenhum método configurado: precisa configurar o app autenticador
        if (mandatory && !app && !email) {
            log.info("MFA setup obrigatório: " + user.getEmail());
            return ResponseEntity.ok(LoginResponseDto.requiresSetup(mfaTokenStore.createToken(user.getId())));
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

        boolean ok = "EMAIL".equalsIgnoreCase(dto.getMethod())
            ? emailMfaCodeStore.validate(userId, dto.getTotpCode())
            : mfaService.validateCode(userId, dto.getTotpCode());

        if (!ok) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(userService.findById(userId))));
    }

    private void sendEmailCode(UserModel user) {
        String code = emailMfaCodeStore.generateCode(user.getId());
        emailService.sendMfaCodeEmail(user.getEmail(), code);
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<MfaSetupResponseDto> setupMfa(@RequestBody MfaVerifyDto dto) {
        int userId = mfaTokenStore.consume(dto.getMfaToken());
        if (userId < 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UserModel user   = userService.findById(userId);
        MfaSetupResponseDto setup = mfaService.generateSetup(userId, user.getEmail());
        String confirmToken = mfaTokenStore.createToken(userId);
        return ResponseEntity.ok(new MfaSetupResponseDto(
            setup.getQrCodeUri(), setup.getSecret(), confirmToken, setup.getMessage()));
    }

    @PostMapping("/mfa/confirm")
    public ResponseEntity<LoginResponseDto> confirmSetup(@Valid @RequestBody MfaVerifyDto dto) {
        int userId = mfaTokenStore.consume(dto.getMfaToken());
        if (userId < 0) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!mfaService.confirmSetup(userId, dto.getTotpCode()))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UserModel user = userService.findById(userId);
        log.info("2FA configurado: " + user.getEmail());
        return ResponseEntity.ok(LoginResponseDto.withJwt(generateJwt(user)));
    }

    private String generateJwt(UserModel user) {
        UserDetails ud = userDetailsService.loadUserByUsername(user.getEmail());
        String jwt = jwtService.generateToken(ud, user.getFullname(), user.getRole(), user.getEmail());
        if (jwt == null || jwt.isEmpty())
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar token");
        return jwt;
    }
}
