package br.com.faitec.falacidade.domain.dto.user;

import jakarta.validation.constraints.NotBlank;

/** Payload para confirmar nova senha com o token do e-mail — POST /api/user/password-reset/confirm. */
public class PasswordResetConfirmDto {
    @NotBlank private String token;
    @NotBlank private String newPassword;
    public String getToken()      { return token; }
    public void   setToken(String v)      { this.token = v; }
    public String getNewPassword(){ return newPassword; }
    public void   setNewPassword(String v){ this.newPassword = v; }
}
