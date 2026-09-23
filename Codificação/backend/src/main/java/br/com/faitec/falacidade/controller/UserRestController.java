package br.com.faitec.falacidade.controller;

import br.com.faitec.falacidade.domain.Municipality;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.MfaVerifyDto;
import br.com.faitec.falacidade.domain.dto.user.*;
import br.com.faitec.falacidade.implementation.service.mfa.EmailMfaCodeStore;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import br.com.faitec.falacidade.port.service.password.PasswordResetService;
import br.com.faitec.falacidade.port.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
    public ResponseEntity<?> register(@Valid @RequestBody RegisterUserDto dto) {
        UserModel entity = dto.toUserModel();
        int id;
        try { id = userService.create(entity); }
        catch (IllegalStateException e) { return conflict(e); }
        if (id < 0) return ResponseEntity.badRequest().build();
        try { emailService.sendWelcomeEmail(entity.getEmail(), entity.getFullname()); }
        catch (Exception ignored) {}
        URI uri = ServletUriComponentsBuilder
            .fromCurrentRequest().replacePath("/api/user/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(uri).build();
    }

    /**
     * RF25: cria contas da administração pública.
     *
     * O Super Administrador cria qualquer perfil, em qualquer município — é
     * quem nomeia os Super Administradores e define o município de cada conta.
     * O administrador municipal monta a equipe do seu próprio município,
     * funcionários e outros administradores, e não alcança outra cidade.
     */
    @PostMapping("/employee")
    public ResponseEntity<?> createStaff(@Valid @RequestBody CreateEmployeeDto dto, Authentication auth) {
        UserModel requester = getAuthenticatedUser(auth);
        if (requester == null) return ResponseEntity.status(401).build();

        UserModel entity;
        try { entity = dto.toUserModel(); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }

        if (!requester.isSuperAdmin()) {
            if (entity.getRole() != UserModel.UserRole.EMPLOYEE
             && entity.getRole() != UserModel.UserRole.ADMINISTRATOR)
                return forbidden("Apenas o Super Administrador cadastra Super Administradores.");
            if (!Municipality.same(requester.getCity(), requester.getState(),
                                   entity.getCity(), entity.getState()))
                return forbidden("Você só pode cadastrar contas do seu município.");
        } else if (entity.getRole() != UserModel.UserRole.SUPER_ADMIN
                && (entity.getCity() == null || entity.getCity().isBlank())) {
            // O município é o que delimita o trabalho do administrador e do
            // funcionário; só o Super Administrador existe sem ele.
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", "Informe o município da conta."));
        }
        int id;
        try { id = userService.create(entity); }
        catch (IllegalStateException e) { return conflict(e); }
        if (id < 0) return ResponseEntity.badRequest().build();
        // Mesmo tratamento do /register: a falha no e-mail não desfaz o cadastro.
        try {
            emailService.sendStaffWelcomeEmail(entity.getEmail(), entity.getFullname(),
                                               entity.getRole(), entity.getCity());
        } catch (Exception ignored) {}
        URI uri = ServletUriComponentsBuilder
            .fromCurrentRequest().replacePath("/api/user/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(uri).build();
    }

    private ResponseEntity<java.util.Map<String, String>> forbidden(String motivo) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error", motivo));
    }

    /**
     * RF25: quem pode administrar a conta alvo. O Super Administrador alcança
     * todas; o administrador municipal, apenas as contas do seu município — e
     * nunca a de um Super Administrador.
     */
    private boolean canManage(UserModel requester, UserModel target) {
        if (requester == null || target == null) return false;
        if (requester.isSuperAdmin()) return true;
        if (requester.getRole() != UserModel.UserRole.ADMINISTRATOR) return false;
        if (target.isSuperAdmin()) return false;
        return Municipality.same(requester.getCity(), requester.getState(),
                                 target.getCity(), target.getState());
    }

    /**
     * Avisa o titular de que a sua conta foi alterada por outra pessoa, dizendo
     * o que mudou e quem mudou. Toda alteração feita por terceiro passa por
     * aqui — dados de perfil, perfil de acesso e ativação —, porque quem
     * descobre a alteração pela consequência não tem como contestá-la a tempo.
     *
     * A falha no envio não desfaz a alteração: o mesmo critério já adotado no
     * cadastro da equipe e no registro de ocorrência.
     */
    private void notifyAccountChange(UserModel target, List<String> alteracoes, UserModel requester) {
        if (target == null || alteracoes.isEmpty()) return;
        if (requester != null && requester.getId() == target.getId()) return;
        if (target.getEmail() == null || target.getEmail().isBlank()) return;
        try {
            emailService.sendAccountChangedEmail(
                target.getEmail(), target.getFullname(), alteracoes,
                requester == null ? null : requester.getFullname(),
                requester == null ? null : requester.getEmail());
        } catch (Exception ignored) { }
    }

    /** O que mudou de fato, em linguagem de tela — campo em branco não é alteração. */
    private List<String> diff(UserModel antes, UserModel depois) {
        List<String> mudou = new ArrayList<>();
        addChange(mudou, "Nome",        antes.getFullname(),     depois.getFullname());
        addChange(mudou, "Telefone",    antes.getPhoneNumber(),  depois.getPhoneNumber());
        addChange(mudou, "CEP",         antes.getCep(),          depois.getCep());
        addChange(mudou, "Logradouro",  antes.getStreet(),       depois.getStreet());
        addChange(mudou, "Número",      antes.getNumber(),       depois.getNumber());
        addChange(mudou, "Bairro",      antes.getNeighborhood(), depois.getNeighborhood());
        addChange(mudou, "Município",   municipality(antes),     municipality(depois));
        return mudou;
    }

    private void addChange(List<String> destino, String rotulo, String antes, String depois) {
        String a = antes  == null ? "" : antes.trim();
        String d = depois == null ? "" : depois.trim();
        if (d.isEmpty() || a.equals(d)) return;
        destino.add(rotulo + ": " + (a.isEmpty() ? "(em branco)" : a) + " \u2192 " + d);
    }

    private String municipality(UserModel user) {
        if (user.getCity() == null || user.getCity().isBlank()) return "";
        return user.getState() == null || user.getState().isBlank()
            ? user.getCity() : user.getCity() + "/" + user.getState();
    }

    private String perfil(UserModel.UserRole role) {
        if (role == null) return "não definido";
        return switch (role) {
            case SUPER_ADMIN    -> "Super Administrador";
            case ADMINISTRATOR  -> "Administrador";
            case EMPLOYEE       -> "Funcionário";
            case CITIZEN        -> "Cidadão";
        };
    }

    /** E-mail duplicado: 409 com o motivo, para a tela dizer o que houve. */
    private ResponseEntity<java.util.Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(java.util.Map.of("error", e.getMessage()));
    }

    /**
     * RF15: lista as contas (inclusive inativas). O Super Administrador vê
     * todas; o administrador municipal, apenas as do seu município (RF25).
     */
    @GetMapping
    public ResponseEntity<java.util.List<UserModel>> getAll(Authentication auth) {
        UserModel requester = getAuthenticatedUser(auth);
        if (requester == null) return ResponseEntity.status(401).build();
        java.util.List<UserModel> users = userService.findAllUsers().stream()
            .filter(u -> canManage(requester, u))
            .toList();
        users.forEach(u -> { u.setPassword(null); u.setMfaSecret(null); });
        return ResponseEntity.ok(users);
    }

    /** RF15: ativa/inativa a conta do usuário, dentro do alcance de quem pede. */
    @PutMapping("/{id}/active")
    public ResponseEntity<Void> setActive(@PathVariable int id,
                                          @RequestBody java.util.Map<String, Boolean> body,
                                          Authentication auth) {
        Boolean active = body.get("active");
        if (active == null) return ResponseEntity.badRequest().build();
        UserModel requester = getAuthenticatedUser(auth);
        UserModel target = userService.findById(id);
        if (!canManage(requester, target)) return ResponseEntity.status(403).build();
        userService.setActive(id, active);
        notifyAccountChange(target, List.of(active ? "Conta reativada" : "Conta inativada"), requester);
        return ResponseEntity.noContent().build();
    }

    /**
     * RF25: troca o perfil da conta, dentro do alcance de quem pede.
     *
     * Endpoint próprio, e não um campo do PUT /api/user/{id}: aquele o próprio
     * usuário chama para editar os seus dados, e perfil ali seria auto-promoção.
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<?> setRole(@PathVariable int id,
                                     @RequestBody java.util.Map<String, String> body,
                                     Authentication auth) {
        UserModel.UserRole role;
        try { role = UserModel.UserRole.valueOf(String.valueOf(body.get("role"))); }
        catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        }

        UserModel requester = getAuthenticatedUser(auth);
        if (requester == null) return ResponseEntity.status(401).build();
        // Mudar o próprio perfil é se promover — ou se trancar para fora da tela.
        if (requester.getId() == id)
            return forbidden("Você não pode alterar o seu próprio perfil.");
        UserModel target = userService.findById(id);
        if (!canManage(requester, target)) return ResponseEntity.status(403).build();
        if (role == UserModel.UserRole.SUPER_ADMIN && !requester.isSuperAdmin())
            return forbidden("Apenas o Super Administrador nomeia Super Administradores.");

        userService.setRole(id, role);
        notifyAccountChange(target,
            List.of("Perfil: " + perfil(target.getRole()) + " \u2192 " + perfil(role)), requester);
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
    public ResponseEntity<Void> delete(@PathVariable int id, Authentication auth) {
        UserModel requester = getAuthenticatedUser(auth);
        UserModel target    = userService.findById(id);
        if (target == null) return ResponseEntity.noContent().build();
        // O cidadão pode excluir a própria conta; a alheia, só quem a administra.
        if (requester == null || (requester.getId() != id && !canManage(requester, target)))
            return ResponseEntity.status(403).build();
        // RF15: a conta alheia só sai do sistema depois de inativada — inativar
        // primeiro é o passo que deixa o administrador voltar atrás.
        if (requester.getId() != id && target.isActive())
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
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
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody UpdateUserDto data,
                                    Authentication auth) {
        UserModel requester = getAuthenticatedUser(auth);
        if (requester == null) return ResponseEntity.status(401).build();
        UserModel target = requester.getId() == id ? requester : userService.findById(id);
        if (requester.getId() != id && !canManage(requester, target))
            return ResponseEntity.status(403).build();
        if (target == null) return ResponseEntity.notFound().build();

        UserModel changes = data.toUserModel();

        // RF25: para a equipe o município não é dado de endereço, é a jurisdição
        // — define quais ocorrências a conta enxerga e onde ela pode cadastrar
        // setores. Trocá-lo é transferir a pessoa de prefeitura, decisão que não
        // cabe nem a ela mesma nem ao administrador do município de origem.
        if (target.isStaff() && !requester.isSuperAdmin()) {
            boolean informou = changes.getCity() != null && !changes.getCity().isBlank();
            if (informou && !Municipality.same(target.getCity(), target.getState(),
                                               changes.getCity(), changes.getState()))
                return forbidden("Apenas o Super Administrador altera o município de uma conta da administração.");
            changes.setCity(target.getCity());
            changes.setState(target.getState());
        }

        List<String> alteracoes = diff(target, changes);
        userService.update(id, changes);
        if (requester.getId() != id) notifyAccountChange(target, alteracoes, requester);
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
