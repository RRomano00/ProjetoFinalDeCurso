package br.com.faitec.falacidade.implementation.service.authentication;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveSessionStore {

    private final ConcurrentHashMap<Integer, String> current = new ConcurrentHashMap<>();

    public String open(int userId) {
        final String sessionId = UUID.randomUUID().toString();
        current.put(userId, sessionId);
        return sessionId;
    }

    /**
     * Usuário sem registro não é derrubado — o mapa vive em memória e nasce
     * vazio a cada reinício do servidor. Só se recusa a sessão que
     * comprovadamente foi substituída por um login posterior.
     */
    public boolean superseded(Integer userId, String sessionId) {
        if (userId == null) return false;
        final String active = current.get(userId);
        return active != null && !active.equals(sessionId);
    }
}
