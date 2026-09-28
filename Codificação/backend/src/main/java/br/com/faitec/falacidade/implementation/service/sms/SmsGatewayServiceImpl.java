package br.com.faitec.falacidade.implementation.service.sms;

import br.com.faitec.falacidade.port.service.sms.SmsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Envia pelo app SMS Gateway for Android: um celular com chip da equipe
 * dispara o SMS pelo plano da operadora, sem custo por mensagem.
 */
@Service
public class SmsGatewayServiceImpl implements SmsService {

    private static final Logger log = Logger.getLogger(SmsGatewayServiceImpl.class.getName());

    // Conexão curta: com o celular desligado, a tela avisa em segundos em vez de travar.
    private final HttpClient   client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json   = new ObjectMapper();

    private final boolean enabled;
    private final String  url;
    private final String  authorization;

    public SmsGatewayServiceImpl(
            @Value("${app.sms.enabled:false}") boolean enabled,
            @Value("${app.sms.url:}") String url,
            @Value("${app.sms.username:}") String username,
            @Value("${app.sms.password:}") String password) {
        this.enabled       = enabled;
        this.url           = url;
        this.authorization = "Basic " + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void send(String phoneE164, String text) {
        if (!enabled) throw new IllegalStateException("Envio de SMS desligado");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", authorization)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body(phoneE164, text)))
            .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Gateway de SMS indisponível", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de SMS interrompido", e);
        }
        if (response.statusCode() / 100 != 2) {
            log.warning("Gateway de SMS recusou o envio (" + response.statusCode() + "): " + response.body());
            throw new IllegalStateException("Gateway de SMS recusou o envio: " + response.statusCode());
        }
    }

    private String body(String to, String text) {
        Map<String, Object> payload = Map.of(
            "textMessage",  Map.of("text", text),
            "phoneNumbers", List.of(to));
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o SMS", e);
        }
    }
}
