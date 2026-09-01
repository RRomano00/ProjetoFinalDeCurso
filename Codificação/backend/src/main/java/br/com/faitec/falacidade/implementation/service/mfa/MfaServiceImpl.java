package br.com.faitec.falacidade.implementation.service.mfa;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.domain.dto.auth.MfaSetupResponseDto;
import br.com.faitec.falacidade.port.dao.user.UserDao;
import br.com.faitec.falacidade.port.service.mfa.MfaService;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Service;

@Service
public class MfaServiceImpl implements MfaService {

    /*
     * COMO O TOTP FUNCIONA:
     *
     * 1. Na ativação, geramos um SECRET (string Base32 aleatória de 32 chars).
     *    Esse secret é compartilhado entre o servidor e o app do usuário via QR Code.
     *
     * 2. A cada 30 segundos, o app e o servidor calculam independentemente:
     *    HOTP(secret, floor(unixTime / 30)) = código de 6 dígitos.
     *    Como ambos usam o mesmo secret e o mesmo timestamp, o resultado é igual.
     *
     * 3. Na validação, aceitamos uma janela de ±1 intervalo (30s antes e 30s depois)
     *    para compensar pequenas diferenças de relógio entre dispositivos.
     */

    private static final String ISSUER = "Fala, Cidade!"; // aparece no app autenticador

    private final UserDao userDao;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final TimeProvider    timeProvider    = new SystemTimeProvider();
    private final CodeGenerator   codeGenerator   = new DefaultCodeGenerator();
    private final CodeVerifier    codeVerifier;

    public MfaServiceImpl(UserDao userDao) {
        this.userDao     = userDao;
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // Aceita janela de 1 intervalo = ±30 segundos (tolerância de relógio)
        ((DefaultCodeVerifier) this.codeVerifier).setTimePeriod(30);
        ((DefaultCodeVerifier) this.codeVerifier).setAllowedTimePeriodDiscrepancy(1);
    }

    @Override
    public MfaSetupResponseDto generateSetup(int userId, String userEmail) {
        // Gera novo secret aleatório (32 chars Base32 = 160 bits de entropia)
        String secret = secretGenerator.generate();

        // Salva o secret no banco (ainda não ativado — mfa_setup_done permanece false)
        userDao.updateMfaSecret(userId, secret);

        // Monta o URI otpauth:// que o QR Code vai conter
        // Formato: otpauth://totp/ISSUER:EMAIL?secret=SECRET&issuer=ISSUER&algorithm=SHA1&digits=6&period=30
        QrData qrData = new QrData.Builder()
            .label(userEmail)
            .secret(secret)
            .issuer(ISSUER)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();

        return new MfaSetupResponseDto(
            qrData.getUri(),
            secret,
            "Escaneie o QR Code com Google Authenticator ou Authy. " +
            "Após escanear, informe o código de 6 dígitos para confirmar."
        );
    }

    @Override
    public boolean confirmSetup(int userId, String totpCode) {
        UserModel user = userDao.readById(userId);
        if (user == null || user.getMfaSecret() == null) return false;

        if (!codeVerifier.isValidCode(user.getMfaSecret(), totpCode)) return false;

        userDao.enableMfa(userId);
        return true;
    }

    @Override
    public boolean validateCode(int userId, String totpCode) {
        UserModel user = userDao.readById(userId);
        if (user == null || user.getMfaSecret() == null) return false;
        return codeVerifier.isValidCode(user.getMfaSecret(), totpCode);
    }

    @Override
    public boolean disable(int userId, String totpCode) {
        if (!validateCode(userId, totpCode)) return false;
        userDao.disableMfa(userId);
        return true;
    }

    @Override
    public void setEmailMfa(int userId, boolean enabled) {
        userDao.setEmailMfa(userId, enabled);
    }
}
