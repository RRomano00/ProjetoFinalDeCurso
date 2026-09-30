package br.com.faitec.falacidade.implementation.service.department;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/**
 * Token do link "Ver / responder ocorrência" do e-mail de encaminhamento. As permissões vão
 * dentro dele (grupo, ocorrência encaminhada, departamento, quem encaminhou) e também o endereço
 * do app, gravado no encaminhamento autenticado: os e-mails disparados por quem usa o link não
 * podem apontar para um Origin qualquer (phishing). A chave é derivada da de login, mas
 * diferente: um token nunca vale pelo outro. O prazo de 30 dias é o teto; "ocorrência
 * aberta" é conferido a cada uso, no controller.
 */
@Component
public class DepartmentAccessTokenService {

    public static final String HEADER = "X-Department-Access";
    private static final Duration MAX_TTL = Duration.ofDays(30);

    public record Access(int groupRootId, int focusId, int departmentId, int forwardedBy, String appUrl) {}

    private final SecretKey key;
    private final Duration ttl;

    @Autowired
    public DepartmentAccessTokenService(@Value("${app.jwt.secret}") String secret) {
        this(secret, MAX_TTL);
    }

    public DepartmentAccessTokenService(String secret, Duration ttl) {
        this.key = Keys.hmacShaKeyFor(sha256(secret + "|department-access"));
        this.ttl = ttl;
    }

    public String issue(int groupRootId, int focusId, int departmentId, int forwardedBy, String appUrl) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .setSubject("department:" + departmentId)
            .claim("occ", groupRootId)
            .claim("focus", focusId)
            .claim("app", appUrl)
            .claim("dep", departmentId)
            .claim("by", forwardedBy)
            .setIssuedAt(new Date(now))
            .setExpiration(new Date(now + ttl.toMillis()))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    public Optional<Access> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return Optional.of(new Access(c.get("occ", Integer.class), c.get("focus", Integer.class),
                                          c.get("dep", Integer.class), c.get("by", Integer.class),
                                          c.get("app", String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static byte[] sha256(String s) {
        try { return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
