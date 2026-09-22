package br.com.faitec.falacidade.domain;

import java.text.Normalizer;

/**
 * Comparação de municípios. Fica aqui porque a mesma regra vale para a
 * ocorrência, o departamento e a conta do usuário: o município é o que
 * delimita quem atende o quê (RF24 / RN07).
 */
public final class Municipality {

    private Municipality() { }

    /**
     * Mesmo município? A comparação despreza acentuação, caixa e espaços; a UF
     * só desempata quando os dois lados a informam — os registros anteriores à
     * adoção da UF não podem desaparecer do município a que pertencem.
     */
    public static boolean same(String cityA, String stateA, String cityB, String stateB) {
        if (isBlank(cityA) || isBlank(cityB)) return false;
        if (!fold(cityA).equals(fold(cityB))) return false;
        if (isBlank(stateA) || isBlank(stateB)) return true;
        return stateA.trim().equalsIgnoreCase(stateB.trim());
    }

    /** "itajuba" para "Itajubá": nome de município sem acento nem caixa. */
    public static String fold(String value) {
        if (value == null) return "";
        String semAcento = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                                     .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase();
    }

    private static boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }
}
