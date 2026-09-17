package br.com.faitec.falacidade.domain.dto.auth;

/**
 * Retornado pelo setup do app autenticador (Meu Perfil).
 *   qrCodeUri → escaneado pelo Google Authenticator / Authy
 *   secret    → entrada manual caso a câmera não funcione
 *   message   → instrução para exibir ao usuário
 */
public class MfaSetupResponseDto {
    private String qrCodeUri;
    private String secret;
    private String message;

    public MfaSetupResponseDto(String qrCodeUri, String secret,
                                String message) {
        this.qrCodeUri = qrCodeUri;
        this.secret    = secret;
        this.message   = message;
    }

    public String getQrCodeUri() { return qrCodeUri; }
    public String getSecret()    { return secret; }
    public String getMessage()   { return message; }
}
