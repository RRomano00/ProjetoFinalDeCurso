package br.com.faitec.falacidade.implementation.service.mfa;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MfaTokenStore {

    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final int  MAX_FAILURES = 5;

    private record Entry(int userId, long expiresAt, int failures) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public String createToken(int userId) {
        evictExpired();
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(userId, System.currentTimeMillis() + TTL_MS, 0));
        return token;
    }

    public int consume(String token) {
        Entry entry = store.remove(token);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) return -1;
        return entry.userId();
    }

    public int peek(String token) {
        Entry entry = store.get(token);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) return -1;
        return entry.userId();
    }

    /**
     * Código errado não encerra a etapa: o usuário ainda pode corrigir ou pedir
     * outro código. O teto de erros é o que barra a força bruta nos 6 dígitos.
     */
    public void fail(String token) {
        store.computeIfPresent(token, (k, e) -> e.failures() + 1 >= MAX_FAILURES ? null
            : new Entry(e.userId(), e.expiresAt(), e.failures() + 1));
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
