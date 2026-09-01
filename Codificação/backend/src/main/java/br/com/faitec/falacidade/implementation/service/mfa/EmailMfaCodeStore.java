package br.com.faitec.falacidade.implementation.service.mfa;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armazena temporariamente os códigos de verificação enviados por e-mail (MFA por e-mail).
 * Mapeamento userId → {código de 6 dígitos, expiração}. Em memória, igual ao MfaTokenStore.
 */
@Component
public class EmailMfaCodeStore {

    private static final long TTL_MS = 10 * 60 * 1000L; // 10 minutos
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(String code, long expiresAt) {}

    private final ConcurrentHashMap<Integer, Entry> store = new ConcurrentHashMap<>();

    /** Gera, armazena e retorna um novo código de 6 dígitos para o usuário. */
    public String generateCode(int userId) {
        evictExpired();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.put(userId, new Entry(code, System.currentTimeMillis() + TTL_MS));
        return code;
    }

    /** Valida o código; em caso de sucesso, consome (remove) o código. */
    public boolean validate(int userId, String code) {
        Entry entry = store.get(userId);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) {
            store.remove(userId);
            return false;
        }
        if (!entry.code().equals(code)) return false;
        store.remove(userId);
        return true;
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
