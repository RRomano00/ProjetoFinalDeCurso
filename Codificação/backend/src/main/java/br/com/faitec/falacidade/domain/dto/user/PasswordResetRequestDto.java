package br.com.faitec.falacidade.domain.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class PasswordResetRequestDto {
    @Email @NotBlank private String email;
    public String getEmail() { return email; }
    public void   setEmail(String v) { this.email = v; }
}
