package br.com.faitec.falacidade.implementation.service.authentication;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uma conta, uma sessão. Guarda qual foi o último login de cada usuário para que
 * os anteriores deixem de valer: quem entra de novo — no celular instalado, no
 * navegador do computador, em qualquer aparelho — derruba a sessão que estava
 * aberta, porque o token antigo passa a ser recusado pelo JwtRequestFilter.
 *
 * Por que em memória, como o MfaTokenStore?
 *  - O dado só interessa enquanto houver token em circulação; não é informação
 *    de negócio para ocupar coluna no banco.
 *  - Usuário sem registro (servidor recém-iniciado) não é derrubado: só se
 *    recusa a sessão que comprovadamente foi substituída por um login posterior.
 *
 * ponytail: o mapa é local à instância — com mais de um servidor de aplicação a
 * derrubada só vale na instância que atendeu o login; nesse caso, trocar por Redis.
 */
@Component
public class ActiveSessionStore {

    private final ConcurrentHashMap<Integer, String> current = new ConcurrentHashMap<>();

    /** Abre a sessão do login recém-concluído e, com isso, invalida as anteriores. */
    public String open(int userId) {
        final String sessionId = UUID.randomUUID().toString();
        current.put(userId, sessionId);
        return sessionId;
    }

    /** true quando um login posterior já substituiu a sessão gravada neste token. */
    public boolean superseded(Integer userId, String sessionId) {
        if (userId == null) return false;
        final String active = current.get(userId);
        return active != null && !active.equals(sessionId);
    }
}
