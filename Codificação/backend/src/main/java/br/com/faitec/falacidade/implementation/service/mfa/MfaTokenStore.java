package br.com.faitec.falacidade.implementation.service.mfa;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armazena temporariamente o mapeamento mfaToken → userId durante o
 * segundo step do login (entre a validação de senha e a validação TOTP).
 *
 * Por que em memória e não no banco?
 *  - Tokens duram apenas 5 minutos — persistência seria overhead desnecessário.
 *  - O ConcurrentHashMap é thread-safe para leituras e escritas simultâneas.
 *  - Para múltiplos servidores em produção, trocaria por Redis.
 *
 * Por que não usar a tabela password_reset_token para isso?
 *  - Semântica diferente: um é para redefinir senha (longa duração),
 *    o outro é para o segundo passo do login (5 minutos).
 *  - Misturar os dois causaria confusão e risco de lógica cruzada.
 */
@Component
public class MfaTokenStore {

    private static final long TTL_MS = 5 * 60 * 1000L; // 5 minutos

    private record Entry(int userId, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public String createToken(int userId) {
        evictExpired();
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(userId, System.currentTimeMillis() + TTL_MS));
        return token;
    }

    /** Retorna o userId associado ao token, ou -1 se inválido/expirado. */
    public int consume(String token) {
        Entry entry = store.remove(token);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) return -1;
        return entry.userId();
    }

    /** Igual ao consume, mas NÃO remove o token (usado para enviar/reenviar código por e-mail). */
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
