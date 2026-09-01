package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.implementation.service.authentication.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService")
class JwtServiceTest {

    // JwtService não tem dependências externas — instanciamos direto
    JwtService sut = new JwtService("FalaCidade#Test@SecretKey!MustBe32+Chars");

    UserDetails userDetails;
    String     token;

    @BeforeEach
    void generateToken() {
        userDetails = new User(
            "joao@email.com",
            "$2a$HASH",
            List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))
        );
        token = sut.generateToken(
            userDetails,
            "João Silva",
            UserModel.UserRole.CITIZEN,
            "joao@email.com"
        );
    }

    // ================================================================
    // generateToken()
    // ================================================================

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("retorna string não vazia")
        void notBlank() {
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("token possui 3 partes separadas por ponto (header.payload.signature)")
        void hasThreeParts() {
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("tokens gerados para o mesmo usuário são diferentes (timestamp diferente)")
        void tokensAreDifferent() throws InterruptedException {
            // O 'iat' do JWT tem precisão de segundos — espera > 1s garante timestamps distintos
            Thread.sleep(1100);
            String token2 = sut.generateToken(
                userDetails, "João Silva", UserModel.UserRole.CITIZEN, "joao@email.com");
            assertThat(token).isNotEqualTo(token2);
        }
    }

    // ================================================================
    // getEmailFromToken()
    // ================================================================

    @Nested
    @DisplayName("getEmailFromToken()")
    class GetEmail {

        @Test
        @DisplayName("extrai o e-mail correto do token")
        void extractsEmail() {
            assertThat(sut.getEmailFromToken(token)).isEqualTo("joao@email.com");
        }

        @Test
        @DisplayName("token inválido lança exceção")
        void invalidTokenThrows() {
            assertThatThrownBy(() -> sut.getEmailFromToken("token.invalido.mesmo"))
                .isInstanceOf(Exception.class);
        }
    }

    // ================================================================
    // getExpirationDateFromToken()
    // ================================================================

    @Nested
    @DisplayName("getExpirationDateFromToken()")
    class GetExpiration {

        @Test
        @DisplayName("data de expiração é no futuro (10 horas à frente)")
        void expirationIsInFuture() {
            Date expiration = sut.getExpirationDateFromToken(token);
            assertThat(expiration).isAfter(new Date());
        }

        @Test
        @DisplayName("expiração está aproximadamente 10 horas no futuro (±30 seg de margem)")
        void expirationIsApproximately10Hours() {
            Date expiration = sut.getExpirationDateFromToken(token);
            long diffMs = expiration.getTime() - System.currentTimeMillis();
            long tenHoursMs = 10L * 60 * 60 * 1000;

            assertThat(diffMs)
                .isGreaterThan(tenHoursMs - 30_000)
                .isLessThan(tenHoursMs + 30_000);
        }
    }

    // ================================================================
    // validToken()
    // ================================================================

    @Nested
    @DisplayName("validToken()")
    class ValidToken {

        @Test
        @DisplayName("token válido com mesmo e-mail → true")
        void validForCorrectUser() {
            assertThat(sut.validToken(token, userDetails)).isTrue();
        }

        @Test
        @DisplayName("token válido mas para outro e-mail → false")
        void invalidForDifferentUser() {
            UserDetails other = new User(
                "outro@email.com", "$2a$HASH",
                List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))
            );
            assertThat(sut.validToken(token, other)).isFalse();
        }

        @Test
        @DisplayName("token malformado → lança exceção (não retorna false silenciosamente)")
        void malformedTokenThrows() {
            assertThatThrownBy(() -> sut.validToken("lixo.total.aqui", userDetails))
                .isInstanceOf(Exception.class);
        }
    }

    // ================================================================
    // Claims customizados (fullname, role)
    // ================================================================

    @Nested
    @DisplayName("Claims customizados")
    class CustomClaims {

        @Test
        @DisplayName("claim 'fullname' está no token")
        void fullnameClaim() {
            String fullname = sut.getClaimFromToken(
                token, claims -> claims.get("fullname", String.class));
            assertThat(fullname).isEqualTo("João Silva");
        }

        @Test
        @DisplayName("claim 'role' está no token com valor correto")
        void roleClaim() {
            String role = sut.getClaimFromToken(
                token, claims -> claims.get("role", String.class));
            assertThat(role).isEqualTo("CITIZEN");
        }

        @Test
        @DisplayName("claim 'email' está no token")
        void emailClaim() {
            String email = sut.getClaimFromToken(
                token, claims -> claims.get("email", String.class));
            assertThat(email).isEqualTo("joao@email.com");
        }
    }
}
