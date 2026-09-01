package br.com.faitec.falacidade.port.service.user;

import br.com.faitec.falacidade.domain.UserModel;

public interface ReadByEmailService {

    UserModel findByEmail(final String email);

}
