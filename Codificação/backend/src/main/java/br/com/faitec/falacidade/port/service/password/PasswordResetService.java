package br.com.faitec.falacidade.port.service.password;

public interface PasswordResetService {
    /** Gera token e envia e-mail (RF05) */
    void requestReset(String email);
    /** Valida token e altera a senha (RF05) */
    boolean confirmReset(String token, String newPassword);
}
