package br.com.faitec.falacidade.port.service.user;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.service.crud.CrudService;

public interface UserService extends CrudService<UserModel>, ReadByEmailService, UpdatePasswordService {
    boolean updatePasswordEncoded(int userId, String encodedPassword);

    /** RF15: lista TODOS os usuários (inclusive inativos) para o painel do administrador. */
    java.util.List<UserModel> findAllUsers();

    /** RF15: ativa/inativa a conta do usuário. */
    void setActive(int userId, boolean active);

    /** RF25: troca o perfil da conta. */
    void setRole(int userId, UserModel.UserRole role);

    /** Há equipe cadastrada para atender o município? Usado no aviso de cobertura. */
    boolean hasStaffInCity(String city, String state);
}
