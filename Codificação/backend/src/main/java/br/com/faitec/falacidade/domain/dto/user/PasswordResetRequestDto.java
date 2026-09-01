package br.com.faitec.falacidade.domain.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Payload para solicitar o link de recuperação de senha — POST /api/user/password-reset/request. */
public class PasswordResetRequestDto {
    @Email @NotBlank private String email;
    public String getEmail() { return email; }
    public void   setEmail(String v) { this.email = v; }
}
