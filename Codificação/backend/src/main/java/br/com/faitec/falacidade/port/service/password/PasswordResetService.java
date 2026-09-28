package br.com.faitec.falacidade.port.service.password;

public interface PasswordResetService {
    void requestReset(String email);
    boolean isTokenValid(String token);
    boolean confirmReset(String token, String newPassword);
}
