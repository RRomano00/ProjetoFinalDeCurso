package br.com.faitec.falacidade.port.dao.password;

import br.com.faitec.falacidade.domain.PasswordResetToken;

public interface PasswordResetTokenDao {
    void save(PasswordResetToken token);
    PasswordResetToken findByToken(String token);
    void markUsed(int tokenId);
    void deleteExpiredByUserId(int userId);
}
