package br.com.faitec.falacidade.port.service.sms;

public interface SmsService {

    /** false quando o envio não foi configurado (app.sms.enabled=false). */
    boolean isEnabled();

    /** Envia o texto ao celular; qualquer falha, inclusive o gateway fora do ar, vira IllegalStateException. */
    void send(String phoneE164, String text);
}
