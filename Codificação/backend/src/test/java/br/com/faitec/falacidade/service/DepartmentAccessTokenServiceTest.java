package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.JwtService;
import br.com.faitec.falacidade.implementation.service.department.DepartmentAccessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DepartmentAccessTokenService – link do encaminhamento")
class DepartmentAccessTokenServiceTest {

    static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres!";
    final DepartmentAccessTokenService sut = new DepartmentAccessTokenService(SECRET);

    @Test
    @DisplayName("o token devolve grupo, departamento e quem encaminhou")
    void roundTrip() {
        var access = sut.verify(sut.issue(10, 11, 3, 5, "https://app.exemplo")).orElseThrow();
        assertThat(access.groupRootId()).isEqualTo(10);
        assertThat(access.focusId()).isEqualTo(11);
        assertThat(access.appUrl()).isEqualTo("https://app.exemplo");
        assertThat(access.departmentId()).isEqualTo(3);
        assertThat(access.forwardedBy()).isEqualTo(5);
    }

    @Test
    @DisplayName("expirado, adulterado, vazio ou lixo: nenhum acesso")
    void rejectsInvalid() {
        var expired = new DepartmentAccessTokenService(SECRET, Duration.ofSeconds(-1));
        assertThat(sut.verify(expired.issue(10, 10, 3, 5, "https://app"))).isEmpty();
        String token = sut.issue(10, 10, 3, 5, "https://app");
        assertThat(sut.verify(token.substring(0, token.length() - 2) + "xx")).isEmpty();
        assertThat(sut.verify(null)).isEmpty();
        assertThat(sut.verify("")).isEmpty();
        assertThat(sut.verify("nao-e-um-jwt")).isEmpty();
    }

    @Test
    @DisplayName("token de login não vale como link, e o link não vale como login")
    void keysDoNotCross() {
        JwtService login = new JwtService(SECRET);
        String userToken = login.generateToken(
            User.withUsername("obras@prefeitura.br").password("x").authorities(List.of()).build(),
            "Fulano", UserModel.UserRole.EMPLOYEE, "obras@prefeitura.br", 1, "sid");
        assertThat(sut.verify(userToken)).isEmpty();
        assertThatThrownBy(() -> login.getEmailFromToken(sut.issue(10, 10, 3, 5, "https://app")))
            .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
