package br.com.faitec.falacidade.port.service.mfa;

import br.com.faitec.falacidade.domain.dto.auth.MfaSetupResponseDto;

public interface MfaService {

    /** Gera um novo secret TOTP e salva (não ativado ainda) para o usuário */
    MfaSetupResponseDto generateSetup(int userId, String userEmail);

    /** Confirma o QR Code escaneado: valida código, ativa MFA */
    boolean confirmSetup(int userId, String totpCode);

    /** Valida um código TOTP no login */
    boolean validateCode(int userId, String totpCode);

    /** Desativa o 2FA (apenas CITIZEN pode desativar o próprio) */
    boolean disable(int userId, String totpCode);

    /** Ativa/desativa o MFA por e-mail do usuário. */
    void setEmailMfa(int userId, boolean enabled);
}
