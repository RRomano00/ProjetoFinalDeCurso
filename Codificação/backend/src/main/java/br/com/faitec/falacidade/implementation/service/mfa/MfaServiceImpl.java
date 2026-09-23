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

    private static final String ISSUER = "Fala, Cidade!";

    private final UserDao userDao;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final TimeProvider    timeProvider    = new SystemTimeProvider();
    private final CodeGenerator   codeGenerator   = new DefaultCodeGenerator();
    private final CodeVerifier    codeVerifier;

    public MfaServiceImpl(UserDao userDao) {
        this.userDao     = userDao;
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        ((DefaultCodeVerifier) this.codeVerifier).setTimePeriod(30);
        ((DefaultCodeVerifier) this.codeVerifier).setAllowedTimePeriodDiscrepancy(1);
    }

    @Override
    public MfaSetupResponseDto generateSetup(int userId, String userEmail) {
        String secret = secretGenerator.generate();

        userDao.updateMfaSecret(userId, secret);

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
