package br.com.faitec.falacidade.port.dao.user;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.dao.crud.CrudDao;

public interface UserDao extends CrudDao<UserModel>, ReadByEmailDao, UpdatePasswordDao {

    void updateMfaSecret(int userId, String secret);

    void enableMfa(int userId);

    void disableMfa(int userId);

    void setEmailMfa(int userId, boolean enabled);

    void setSmsMfa(int userId, String phone);

    /** O número já é de outra conta, como telefone do perfil ou como celular do SMS? */
    boolean isPhoneInUse(int exceptUserId, String phone);

    void logOccurrenceDeletion(UserModel actor, String protocol, String title, String city, String state);

    java.util.List<UserModel> readAllUsers();

    boolean existsStaffInCity(String city, String state);

    void setActive(int userId, boolean active);

    void setRole(int userId, UserModel.UserRole role);

    void log(String action, UserModel actor, Integer targetId, UserModel target);

    java.util.List<java.util.Map<String, Object>> readLog();
}
