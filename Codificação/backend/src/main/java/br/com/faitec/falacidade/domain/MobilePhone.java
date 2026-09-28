package br.com.faitec.falacidade.domain;

/** Celular brasileiro no formato E.164 que o gateway de SMS espera: +55, DDD e os 9 dígitos. */
public final class MobilePhone {

    private MobilePhone() { }

    public static String toE164(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 11) d = "55" + d;
        if (d.length() != 13 || !d.startsWith("55")) return null;
        // Nenhum DDD tem zero, e celular começa com 9 — telefone fixo não recebe SMS.
        if (d.charAt(2) == '0' || d.charAt(3) == '0' || d.charAt(4) != '9') return null;
        return "+" + d;
    }

    /** A tela mostra para onde vai o código sem expor o número inteiro. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() != 14) return null;
        return "(" + e164.substring(3, 5) + ") 9****-" + e164.substring(10);
    }
}
