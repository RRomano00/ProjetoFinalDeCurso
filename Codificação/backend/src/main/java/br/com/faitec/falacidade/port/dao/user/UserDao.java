package br.com.faitec.falacidade.port.dao.user;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.dao.crud.CrudDao;

public interface UserDao extends CrudDao<UserModel>, ReadByEmailDao, UpdatePasswordDao {

    void updateMfaSecret(int userId, String secret);

    void enableMfa(int userId);

    void disableMfa(int userId);

    void setEmailMfa(int userId, boolean enabled);

    java.util.List<UserModel> readAllUsers();

    boolean existsStaffInCity(String city, String state);

    void setActive(int userId, boolean active);

    void setRole(int userId, UserModel.UserRole role);
}
