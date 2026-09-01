package br.com.faitec.falacidade.domain.dto.auth;

/**
 * Retornado pelo Step 2b (setup do 2FA).
 *   qrCodeUri → escaneado pelo Google Authenticator / Authy
 *   secret    → entrada manual caso a câmera não funcione
 *   mfaToken  → token temporário para usar no Step 3 (confirm)
 *   message   → instrução para exibir ao usuário
 */
public class MfaSetupResponseDto {
    private String qrCodeUri;
    private String secret;
    private String mfaToken;
    private String message;

    public MfaSetupResponseDto(String qrCodeUri, String secret,
                                String mfaToken, String message) {
        this.qrCodeUri = qrCodeUri;
        this.secret    = secret;
        this.mfaToken  = mfaToken;
        this.message   = message;
    }

    public MfaSetupResponseDto(String qrCodeUri, String secret,
                                String message) {
        this.qrCodeUri = qrCodeUri;
        this.secret    = secret;
        this.message   = message;
    }

    public String getQrCodeUri() { return qrCodeUri; }
    public String getSecret()    { return secret; }
    public String getMfaToken()  { return mfaToken; }
    public String getMessage()   { return message; }
}
