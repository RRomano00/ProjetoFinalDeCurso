package br.com.faitec.falacidade.domain.dto.auth;

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
