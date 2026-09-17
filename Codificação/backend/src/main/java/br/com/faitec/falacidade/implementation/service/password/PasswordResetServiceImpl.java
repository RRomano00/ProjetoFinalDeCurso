package br.com.faitec.falacidade.implementation.service.password;

import br.com.faitec.falacidade.domain.PasswordResetToken;
import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.tracking.AnonymousTrackingCodeService;
import br.com.faitec.falacidade.port.dao.password.PasswordResetTokenDao;
import br.com.faitec.falacidade.port.service.email.EmailService;
import br.com.faitec.falacidade.port.service.password.PasswordResetService;
import br.com.faitec.falacidade.port.service.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserService userService;
    private final PasswordResetTokenDao tokenDao;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AnonymousTrackingCodeService codeService;

    @Value("${app.password-reset.expiration-minutes:30}")
    private int expirationMinutes;

    public PasswordResetServiceImpl(UserService userService,
                                    PasswordResetTokenDao tokenDao,
                                    EmailService emailService,
                                    PasswordEncoder passwordEncoder,
                                    AnonymousTrackingCodeService codeService) {
        this.userService = userService;
        this.tokenDao = tokenDao;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.codeService = codeService;
    }

    @Override
    public void requestReset(String email) {
        UserModel user = userService.findByEmail(email);
        // Não revelamos se o e-mail existe ou não (segurança)
        if (user == null) return;

        // Limpa tokens antigos deste usuário
        tokenDao.deleteExpiredByUserId(user.getId());

        // Código curto de 8 chars (o mesmo gerador do rastreamento anônimo): é isso
        // que a pessoa digita de volta na tela. Um link para localhost não abre em
        // lugar nenhum e ainda cheira a phishing para o filtro de spam.
        String rawToken = codeService.generateCode();

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(rawToken);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));

        tokenDao.save(token);

        emailService.sendPasswordResetEmail(email, rawToken);
    }

    @Override
    public boolean confirmReset(String rawToken, String newPassword) {
        if (rawToken == null) return false;
        // Código digitado à mão: espaço nas pontas e minúsculas não podem reprovar.
        PasswordResetToken token = tokenDao.findByToken(rawToken.trim().toUpperCase());

        if (token == null || token.isExpired() || token.isUsed()) {
            return false;
        }

        // Valida complexidade de senha (RNF17 – mínimo 8 chars, letra, número e especial)
        if (!isPasswordValid(newPassword)) {
            return false;
        }

        String encoded = passwordEncoder.encode(newPassword);
        boolean updated = userService.updatePasswordEncoded(token.getUserId(), encoded);

        if (updated) {
            tokenDao.markUsed(token.getId());
        }

        return updated;
    }

    private boolean isPasswordValid(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasLetter  = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        return hasLetter && hasDigit && hasSpecial;
    }
}
