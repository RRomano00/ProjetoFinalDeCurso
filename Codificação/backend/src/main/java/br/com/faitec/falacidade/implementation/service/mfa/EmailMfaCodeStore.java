package br.com.faitec.falacidade.implementation.service.mfa;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Códigos de 6 dígitos de todos os canais (e-mail e SMS); o nome ficou do
 * primeiro. Cada canal tem o seu código, e o do SMS fica preso ao número para
 * onde foi enviado — assim o código recebido num celular não ativa outro.
 */
@Component
public class EmailMfaCodeStore {

    public static final String EMAIL = "EMAIL";
    public static final String SMS   = "SMS";

    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(String code, String target, long createdAt, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public String generateCode(int userId) {
        return generateCode(userId, EMAIL, null);
    }

    public boolean validate(int userId, String code) {
        return validate(userId, EMAIL, null, code);
    }

    public String generateCode(int userId, String channel, String target) {
        evictExpired();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        long now = System.currentTimeMillis();
        store.put(key(userId, channel), new Entry(code, target, now, now + TTL_MS));
        return code;
    }

    public boolean validate(int userId, String channel, String target, String code) {
        String key = key(userId, channel);
        Entry entry = store.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) {
            store.remove(key);
            return false;
        }
        if (!entry.code().equals(code) || !Objects.equals(entry.target(), target)) return false;
        store.remove(key);
        return true;
    }

    public boolean sentWithin(int userId, String channel, long ms) {
        Entry entry = store.get(key(userId, channel));
        return entry != null && System.currentTimeMillis() - entry.createdAt() < ms;
    }

    public void discard(int userId, String channel) {
        store.remove(key(userId, channel));
    }

    private String key(int userId, String channel) {
        return userId + ":" + channel;
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
