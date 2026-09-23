package br.com.faitec.falacidade.domain.dto.auth;

public class LoginResponseDto {

    private String  token;
    private boolean requiresMfa;
    private String  mfaToken;
    private boolean mfaAppAvailable;
    private boolean mfaEmailAvailable;

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

    public String  getToken()             { return token; }
    public boolean isRequiresMfa()        { return requiresMfa; }
    public String  getMfaToken()          { return mfaToken; }
    public boolean isMfaAppAvailable()    { return mfaAppAvailable; }
    public boolean isMfaEmailAvailable()  { return mfaEmailAvailable; }
}
