package br.com.faitec.falacidade.port.dao.user;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.dao.crud.CrudDao;

public interface UserDao extends CrudDao<UserModel>, ReadByEmailDao, UpdatePasswordDao {

    /** Salva o secret TOTP gerado (antes da confirmação) */
    void updateMfaSecret(int userId, String secret);

    /** Marca mfa_enabled=true e mfa_setup_done=true após confirmação do QR */
    void enableMfa(int userId);

    /** Limpa secret, desativa mfa_enabled e mfa_setup_done */
    void disableMfa(int userId);

    /** Ativa/desativa o MFA por e-mail */
    void setEmailMfa(int userId, boolean enabled);

    /** RF15: lista TODOS os usuários (inclusive inativos) para o painel do administrador. */
    java.util.List<UserModel> readAllUsers();

    /** RF15: ativa/inativa a conta do usuário. */
    void setActive(int userId, boolean active);
}
