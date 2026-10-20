package br.com.faitec.falacidade.domain;

/**
 * Regra única de senha: cadastro, troca e recuperação passam por aqui.
 * O front repete a mesma regra em shared/br-mask.ts (PASSWORD_PATTERN).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() { }

    /** Ao menos 8 caracteres, com letra, número e um caractere que não seja nenhum dos dois. */
    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) return false;
        boolean hasLetter  = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        return hasLetter && hasDigit && hasSpecial;
    }
}
