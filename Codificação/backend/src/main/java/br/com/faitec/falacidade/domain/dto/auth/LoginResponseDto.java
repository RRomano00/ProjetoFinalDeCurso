package br.com.faitec.falacidade.domain.dto.auth;

/**
 * Resposta do POST /api/authenticate (Step 1).
 *
 * Cenários possíveis:
 *   token preenchido          → login completo, sem 2FA
 *   requiresMfa = true        → usuário tem 2FA ativo, envie o código TOTP
 *   requiresMfaSetup = true   → EMPLOYEE/ADMIN sem 2FA configurado, configure agora
 */
public class LoginResponseDto {

    private String  token;
    private boolean requiresMfa;
    private boolean requiresMfaSetup;
    private String  mfaToken;
    private boolean mfaAppAvailable;    // usuário tem app autenticador ativo
    private boolean mfaEmailAvailable;  // usuário tem MFA por e-mail ativo

    private LoginResponseDto() {}

    public static LoginResponseDto withJwt(String jwt) {
        LoginResponseDto r = new LoginResponseDto();
        r.token = jwt;
        return r;
    }

    public static LoginResponseDto requiresMfa(String mfaToken, boolean appAvailable, boolean emailAvailable) {
        LoginResponseDto r = new LoginResponseDto();
        r.requiresMfa       = true;
        r.mfaToken          = mfaToken;
        r.mfaAppAvailable   = appAvailable;
        r.mfaEmailAvailable = emailAvailable;
        return r;
    }

    public static LoginResponseDto requiresSetup(String mfaToken) {
        LoginResponseDto r = new LoginResponseDto();
        r.requiresMfaSetup = true;
        r.mfaToken         = mfaToken;
        return r;
    }

    public String  getToken()             { return token; }
    public boolean isRequiresMfa()        { return requiresMfa; }
    public boolean isRequiresMfaSetup()   { return requiresMfaSetup; }
    public String  getMfaToken()          { return mfaToken; }
    public boolean isMfaAppAvailable()    { return mfaAppAvailable; }
    public boolean isMfaEmailAvailable()  { return mfaEmailAvailable; }
}
