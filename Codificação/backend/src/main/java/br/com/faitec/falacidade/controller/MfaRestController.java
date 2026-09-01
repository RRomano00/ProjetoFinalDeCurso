package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.MfaSetupResponseDto;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mfa")
public class MfaRestController {

    private final MfaService        mfaService;
    private final UserService       userService;
    private final EmailService      emailService;
    private final EmailMfaCodeStore emailMfaCodeStore;

    public MfaRestController(MfaService mfaService, UserService userService,
                             EmailService emailService, EmailMfaCodeStore emailMfaCodeStore) {
        this.mfaService        = mfaService;
        this.userService       = userService;
        this.emailService      = emailService;
        this.emailMfaCodeStore = emailMfaCodeStore;
    }

    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponseDto> setup(Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        if (user.isMfaSetupDone()) return ResponseEntity.badRequest().build();
        MfaSetupResponseDto s = mfaService.generateSetup(user.getId(), user.getEmail());
        return ResponseEntity.ok(new MfaSetupResponseDto(s.getQrCodeUri(), s.getSecret(), null, s.getMessage()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody MfaVerifyDto dto, Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        return mfaService.confirmSetup(user.getId(), dto.getTotpCode())
            ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
    }

    /** Estado atual do 2FA do usuário (para a tela Meu Perfil). */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of(
            "appActive",   user.isAppMfaActive(),
            "emailActive", user.isEmailMfaActive(),
            "role",        user.getRole().name()
        ));
    }

    /** Envia um código ao e-mail para CONFIRMAR a ativação do MFA por e-mail. */
    @PostMapping("/email/send-enable-code")
    public ResponseEntity<Void> sendEmailEnableCode(Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        if (user.isEmailMfaActive()) return ResponseEntity.badRequest().build();
        String code = emailMfaCodeStore.generateCode(user.getId());
        emailService.sendMfaCodeEmail(user.getEmail(), code);
        return ResponseEntity.ok().build();
    }

    /** Ativa o MFA por e-mail. Exige o código enviado ao próprio e-mail. */
    @PostMapping("/email")
    public ResponseEntity<Void> enableEmail(@RequestBody MfaVerifyDto dto, Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        if (user.isEmailMfaActive()) return ResponseEntity.badRequest().build();
        if (!emailMfaCodeStore.validate(user.getId(), dto.getTotpCode()))
            return ResponseEntity.status(401).build();
        mfaService.setEmailMfa(user.getId(), true);
        return ResponseEntity.ok().build();
    }

    /** Envia um código ao e-mail para CONFIRMAR a desativação do MFA por e-mail. */
    @PostMapping("/email/send-code")
    public ResponseEntity<Void> sendEmailDisableCode(Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        if (!user.isEmailMfaActive()) return ResponseEntity.badRequest().build();
        String code = emailMfaCodeStore.generateCode(user.getId());
        emailService.sendMfaDeactivationEmail(user.getEmail(), code);
        return ResponseEntity.ok().build();
    }

    /**
     * Desativa o MFA por e-mail. Exige o código enviado ao próprio e-mail (0.5).
     * Admin/Funcionário não podem ficar sem nenhum MFA: bloqueia se o app não estiver ativo.
     */
    @DeleteMapping("/email")
    public ResponseEntity<Void> disableEmail(@RequestBody MfaVerifyDto dto, Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        if (!user.isEmailMfaActive()) return ResponseEntity.badRequest().build();
        if (isStaff(user) && !user.isAppMfaActive())
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // precisa manter ao menos 1 método
        if (!emailMfaCodeStore.validate(user.getId(), dto.getTotpCode()))
            return ResponseEntity.status(401).build();
        mfaService.setEmailMfa(user.getId(), false);
        return ResponseEntity.ok().build();
    }

    /**
     * Desativa o MFA por aplicativo. Exige o código TOTP do próprio app (0.5).
     * Admin/Funcionário não podem ficar sem nenhum MFA: bloqueia se o e-mail não estiver ativo.
     */
    @DeleteMapping
    public ResponseEntity<Void> disable(@Valid @RequestBody MfaVerifyDto dto, Authentication auth) {
        UserModel user = getUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        if (!user.isAppMfaActive()) return ResponseEntity.badRequest().build();
        if (isStaff(user) && !user.isEmailMfaActive())
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // precisa manter ao menos 1 método
        return mfaService.disable(user.getId(), dto.getTotpCode())
            ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
    }

    private boolean isStaff(UserModel user) {
        return user.getRole() == UserModel.UserRole.EMPLOYEE
            || user.getRole() == UserModel.UserRole.ADMINISTRATOR;
    }

    private UserModel getUser(Authentication auth) {
        if (auth == null) return null;
        return userService.findByEmail(auth.getName());
    }
}
