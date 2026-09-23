package br.com.faitec.falacidade.implementation.service.mfa;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MfaTokenStore {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private record Entry(int userId, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public String createToken(int userId) {
        evictExpired();
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(userId, System.currentTimeMillis() + TTL_MS));
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

    private void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
