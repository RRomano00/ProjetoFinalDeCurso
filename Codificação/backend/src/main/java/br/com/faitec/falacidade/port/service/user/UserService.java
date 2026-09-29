package br.com.faitec.falacidade.port.service.user;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.service.crud.CrudService;

public interface UserService extends CrudService<UserModel>, ReadByEmailService, UpdatePasswordService {
    boolean updatePasswordEncoded(int userId, String encodedPassword);

    java.util.List<UserModel> findAllUsers();

    void setActive(int userId, boolean active);

    void setRole(int userId, UserModel.UserRole role);

    boolean hasStaffInCity(String city, String state);

    boolean isPhoneInUse(int exceptUserId, String phone);

    void logOccurrenceDeletion(UserModel actor, String protocol, String title, String city, String state);

    void log(String action, UserModel actor, Integer targetId, UserModel target);

    java.util.List<java.util.Map<String, Object>> findLog();
}
