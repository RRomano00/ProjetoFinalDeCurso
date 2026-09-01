package br.com.faitec.falacidade.port.dao.user;

import br.com.faitec.falacidade.domain.UserModel;

public interface ReadByEmailDao {

    UserModel readByEmail(final String email);

}
