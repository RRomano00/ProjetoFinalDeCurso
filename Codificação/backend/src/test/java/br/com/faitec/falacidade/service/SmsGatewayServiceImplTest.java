package br.com.faitec.falacidade.service;

import br.com.faitec.falacidade.implementation.service.sms.SmsGatewayServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SmsGatewayServiceImpl – envio pelo SMS Gateway for Android")
class SmsGatewayServiceImplTest {

    HttpServer server;
    volatile String lastPath, lastAuth, lastBody;
    volatile int status = 202;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath = ex.getRequestURI().getPath();
            lastAuth = ex.getRequestHeaders().getFirst("Authorization");
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = "{\"id\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private SmsGatewayServiceImpl sut(boolean enabled) {
        return new SmsGatewayServiceImpl(enabled,
            "http://127.0.0.1:" + server.getAddress().getPort() + "/message", "sms", "senha");
    }

    @Test
    @DisplayName("envia texto e número no formato do gateway, com autenticação básica")
    void enviaMensagem() throws Exception {
        sut(true).send("+5535998761234", "FalaCidade: seu codigo e 042917");

        assertThat(lastPath).isEqualTo("/message");
        assertThat(lastAuth).isEqualTo("Basic "
            + Base64.getEncoder().encodeToString("sms:senha".getBytes(StandardCharsets.UTF_8)));
        JsonNode b = new ObjectMapper().readTree(lastBody);
        assertThat(b.at("/textMessage/text").asText()).isEqualTo("FalaCidade: seu codigo e 042917");
        assertThat(b.at("/phoneNumbers/0").asText()).isEqualTo("+5535998761234");
        assertThat(b.path("phoneNumbers").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("resposta de erro do gateway vira exceção")
    void respostaDeErroViraExcecao() {
        status = 500;
        assertThatThrownBy(() -> sut(true).send("+5535998761234", "x"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("celular fora do ar vira exceção")
    void celularForaDoArViraExcecao() {
        // Porta 1 fechada: conexão recusada, como com o celular desligado.
        SmsGatewayServiceImpl semCelular = new SmsGatewayServiceImpl(true,
            "http://127.0.0.1:1/message", "sms", "senha");
        assertThatThrownBy(() -> semCelular.send("+5535998761234", "x"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("desligado não chama o gateway")
    void desligadoNaoChamaOGateway() {
        assertThatThrownBy(() -> sut(false).send("+5535998761234", "x"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(lastPath).isNull();
    }
}
