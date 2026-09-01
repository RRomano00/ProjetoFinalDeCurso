package br.com.faitec.falacidade.port.service.authentication;

import br.com.faitec.falacidade.domain.UserModel;

public interface AuthenticationService {

    UserModel authenticate(final String email, final String password);

}
