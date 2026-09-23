package br.com.faitec.falacidade.domain;

import java.text.Normalizer;

public final class Municipality {

    private Municipality() { }

    /**
     * Compara sem acento e sem caixa. A UF só desempata quando os dois lados a
     * informam: os registros anteriores à adoção da UF ficaram sem ela e não
     * podem desaparecer do município a que pertencem.
     */
    public static boolean same(String cityA, String stateA, String cityB, String stateB) {
        if (isBlank(cityA) || isBlank(cityB)) return false;
        if (!fold(cityA).equals(fold(cityB))) return false;
        if (isBlank(stateA) || isBlank(stateB)) return true;
        return stateA.trim().equalsIgnoreCase(stateB.trim());
    }

    public static String fold(String value) {
        if (value == null) return "";
        String semAcento = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                                     .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase();
    }

    private static boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }
}
