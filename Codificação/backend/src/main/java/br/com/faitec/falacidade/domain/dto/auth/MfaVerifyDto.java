package br.com.faitec.falacidade.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MfaVerifyDto {

    @Pattern(regexp = "\\d{6}", message = "Código deve ter exatamente 6 dígitos")
    private String totpCode;

    private String mfaToken;

    private String method;

    public String getTotpCode()              { return totpCode; }
    public void   setTotpCode(String c)      { this.totpCode = c; }
    public String getMfaToken()              { return mfaToken; }
    public void   setMfaToken(String t)      { this.mfaToken = t; }
    public String getMethod()                { return method; }
    public void   setMethod(String m)        { this.method = m; }
}
