package br.com.faitec.falacidade.port.service.mfa;

import br.com.faitec.falacidade.domain.dto.auth.MfaSetupResponseDto;

public interface MfaService {

    MfaSetupResponseDto generateSetup(int userId, String userEmail);

    boolean confirmSetup(int userId, String totpCode);

    boolean validateCode(int userId, String totpCode);

    boolean disable(int userId, String totpCode);

    void setEmailMfa(int userId, boolean enabled);
}
