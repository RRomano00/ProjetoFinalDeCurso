package br.com.faitec.falacidade.implementation.service.tracking;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Responsável por gerar e verificar o código de rastreamento anônimo.
 *
 * DESIGN DE SEGURANÇA:
 *  - O código tem 8 chars alfanuméricos maiúsculos (ex: "A1B2C3D4").
 *    Espaço de ~2,8 trilhões de combinações — suficiente para impedir
 *    brute force sem rate limiting.
 *  - Apenas o hash SHA-256 é gravado no banco. Assim, mesmo com acesso
 *    ao banco, um invasor não descobre os códigos dos denunciantes.
 *  - O código plain text é retornado UMA SÓ VEZ na resposta do POST.
 *    Após isso, não existe mais nenhum lugar que o contenha.
 *  - A comparação é feita sempre hash vs hash (igual ao BCrypt de senha).
 */
@Component
public class AnonymousTrackingCodeService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    // Removidos: O, 0, I, 1 — visualmente ambíguos em fontes sem serifa
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    /**
     * Gera um código aleatório de 8 caracteres.
     * Ex: "A3KP7NB2"
     */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Calcula o SHA-256 do código em hexadecimal (64 chars).
     * Usado para persistência e para comparação na consulta.
     */
    public String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é garantido pela JVM (java.security) — nunca vai lançar
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    /**
     * Verifica se um código fornecido pelo cidadão bate com o hash armazenado.
     */
    public boolean matches(String plainCode, String storedHash) {
        if (plainCode == null || storedHash == null) return false;
        return storedHash.equals(hash(plainCode.toUpperCase().trim()));
    }
}
