package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.domain.dto.user.*;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.password.PasswordResetService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;

@RestController
@RequestMapping("/api/user")
public class UserRestController {

    private final UserService          userService;
    private final PasswordResetService passwordResetService;
    private final EmailService         emailService;
    private final MfaService           mfaService;
    private final EmailMfaCodeStore    emailMfaCodeStore;

    public UserRestController(UserService userService,
                              PasswordResetService passwordResetService,
                              EmailService emailService,
                              MfaService mfaService,
                              EmailMfaCodeStore emailMfaCodeStore) {
        this.userService          = userService;
        this.passwordResetService = passwordResetService;
        this.emailService         = emailService;
        this.mfaService           = mfaService;
        this.emailMfaCodeStore    = emailMfaCodeStore;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserDto dto) {
        UserModel entity = dto.toUserModel();
        int id = userService.create(entity);
        if (id < 0) return ResponseEntity.badRequest().build();
        try { emailService.sendWelcomeEmail(entity.getEmail(), entity.getFullname()); }
        catch (Exception ignored) {}
        URI uri = ServletUriComponentsBuilder
            .fromCurrentRequest().replacePath("/api/user/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(uri).build();
    }

    @PostMapping("/employee")
    public ResponseEntity<Void> createStaff(@Valid @RequestBody CreateEmployeeDto dto) {
        UserModel entity;
        try { entity = dto.toUserModel(); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
        int id = userService.create(entity);
        if (id < 0) return ResponseEntity.badRequest().build();
        URI uri = ServletUriComponentsBuilder
            .fromCurrentRequest().replacePath("/api/user/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(uri).build();
    }

    /** RF15: lista todos os usuários (inclusive inativos) — restrito ao Administrador. */
    @GetMapping
    public ResponseEntity<java.util.List<UserModel>> getAll() {
        java.util.List<UserModel> users = userService.findAllUsers();
        users.forEach(u -> { u.setPassword(null); u.setMfaSecret(null); });
        return ResponseEntity.ok(users);
    }

    /** RF15: ativa/inativa a conta do usuário — restrito ao Administrador. */
    @PutMapping("/{id}/active")
    public ResponseEntity<Void> setActive(@PathVariable int id,
                                          @RequestBody java.util.Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) return ResponseEntity.badRequest().build();
        userService.setActive(id, active);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserModel> getById(@PathVariable int id) {
        UserModel entity = userService.findById(id);
        if (entity == null) return ResponseEntity.notFound().build();
        entity.setPassword(null);
        entity.setMfaSecret(null);
        return ResponseEntity.ok(entity);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Envia um código ao e-mail para CONFIRMAR a exclusão da própria conta
     * (apenas quando o usuário tem o MFA por e-mail ativo). RF06.
     */
    @PostMapping("/account/delete-code")
    public ResponseEntity<Void> sendAccountDeletionCode(Authentication auth) {
        UserModel user = getAuthenticatedUser(auth);
        if (user == null) return ResponseEntity.status(401).build();
        if (!user.isEmailMfaActive()) return ResponseEntity.badRequest().build();
        String code = emailMfaCodeStore.generateCode(user.getId());
        emailService.sendMfaCodeEmail(user.getEmail(), code);
        return ResponseEntity.ok().build();
    }

    /**
     * Exclui a própria conta do usuário autenticado (RF06).
     * Se o usuário tiver algum MFA ativo, exige o código do método ativo
     * (TOTP do app ou código enviado ao e-mail — qualquer um que estiver ativo).
     */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteOwnAccount(@RequestBody MfaVerifyDto dto, Authentication auth) {
        UserModel user = getAuthenticatedUser(auth);
        if (user == null) return ResponseEntity.status(401).build();

        boolean appActive   = user.isAppMfaActive();
        boolean emailActive = user.isEmailMfaActive();

        if (appActive || emailActive) {
            String code = dto != null ? dto.getTotpCode() : null;
            if (code == null || code.isBlank()) return ResponseEntity.status(401).build();
            boolean ok = false;
            if (appActive)            ok = mfaService.validateCode(user.getId(), code);
            if (!ok && emailActive)   ok = emailMfaCodeStore.validate(user.getId(), code);
            if (!ok) return ResponseEntity.status(401).build();
        }

        userService.delete(user.getId());
        return ResponseEntity.noContent().build();
    }

    private UserModel getAuthenticatedUser(Authentication auth) {
        if (auth == null) return null;
        return userService.findByEmail(auth.getName());
    }

    /** RF04: atualiza os dados de perfil — só o próprio usuário ou um Administrador. */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable int id, @RequestBody UpdateUserDto data,
                                       Authentication auth) {
        UserModel requester = getAuthenticatedUser(auth);
        if (requester == null) return ResponseEntity.status(401).build();
        boolean isAdmin = requester.getRole() == UserModel.UserRole.ADMINISTRATOR;
        if (requester.getId() != id && !isAdmin) return ResponseEntity.status(403).build();
        userService.update(id, data.toUserModel());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/update-password")
    public ResponseEntity<Void> updatePassword(@RequestBody UpdatePasswordDto data) {
        boolean ok = userService.updatePassword(data.getId(), data.getOldPassword(), data.getNewPassword());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserModel> getByEmail(@PathVariable String email) {
        UserModel entity = userService.findByEmail(email);
        if (entity == null) return ResponseEntity.notFound().build();
        entity.setPassword(null);
        entity.setMfaSecret(null);
        return ResponseEntity.ok(entity);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequestDto dto) {
        passwordResetService.requestReset(dto.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmDto dto) {
        boolean ok = passwordResetService.confirmReset(dto.getToken(), dto.getNewPassword());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }
}
