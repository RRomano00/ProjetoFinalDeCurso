package br.com.faitec.falacidade.domain.dto.user;

import jakarta.validation.constraints.NotBlank;

public class PasswordResetConfirmDto {
    @NotBlank private String token;
    @NotBlank private String newPassword;
    public String getToken()      { return token; }
    public void   setToken(String v)      { this.token = v; }
    public String getNewPassword(){ return newPassword; }
    public void   setNewPassword(String v){ this.newPassword = v; }
}
