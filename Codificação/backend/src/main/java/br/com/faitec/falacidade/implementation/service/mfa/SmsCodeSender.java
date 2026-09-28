package br.com.faitec.falacidade.implementation.service.mfa;

import br.com.faitec.falacidade.port.service.sms.SmsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Regras comuns a todo envio de código por SMS: login, perfil e exclusão de conta. */
@Component
public class SmsCodeSender {

    // Rajada de SMS iguais leva a operadora a bloquear o chip do gateway por spam.
    private static final long RESEND_MS = 60_000L;

    private final SmsService        sms;
    private final EmailMfaCodeStore codes;

    public SmsCodeSender(SmsService sms, EmailMfaCodeStore codes) {
        this.sms   = sms;
        this.codes = codes;
    }

    public boolean isEnabled() { return sms.isEnabled(); }

    /** No 429 o código anterior segue válido; na falha ele é descartado para liberar nova tentativa. */
    public HttpStatus send(int userId, String phoneE164) {
        if (!sms.isEnabled()) return HttpStatus.SERVICE_UNAVAILABLE;
        if (codes.sentWithin(userId, EmailMfaCodeStore.SMS, RESEND_MS)) return HttpStatus.TOO_MANY_REQUESTS;
        String code = codes.generateCode(userId, EmailMfaCodeStore.SMS, phoneE164);
        try {
            sms.send(phoneE164, text(code));
            return HttpStatus.OK;
        } catch (IllegalStateException e) {
            codes.discard(userId, EmailMfaCodeStore.SMS);
            return HttpStatus.BAD_GATEWAY;
        }
    }

    // Sem acento de propósito: acento muda a codificação e o limite do SMS cai de 160 para 70 caracteres.
    private String text(String code) {
        return "FalaCidade: seu codigo de verificacao e " + code
             + ". Vale por 10 minutos. Nao compartilhe.";
    }
}
