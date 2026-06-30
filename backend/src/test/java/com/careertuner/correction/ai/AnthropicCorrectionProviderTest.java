package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class AnthropicCorrectionProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("calls Haiku once and validates the trained correction output contract")
    void correct_callsHaikuAndMapsOutput() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] response = responseJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        CorrectionAnthropicProperties properties = new CorrectionAnthropicProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/v1");
        CorrectionAiProperties correctionProperties = new CorrectionAiProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        AnthropicCorrectionProvider provider = new AnthropicCorrectionProvider(
                properties,
                correctionProperties,
                objectMapper,
                new SelfCorrectionOutputParser(objectMapper));

        CorrectionPayload payload = provider.correct(command());

        assertThat(payload.improvedText()).isEqualTo("개선된 문장");
        assertThat(payload.issues()).containsExactly("근거 확인 필요");
        assertThat(payload.usage().model()).isEqualTo("claude-haiku-4-5-20251001");
        assertThat(payload.usage().totalTokens()).isEqualTo(30);
        assertThat(apiKey.get()).isEqualTo("test-key");
        assertThat(requestBody.get()).contains("SELF_INTRO_CORRECTION", "max_tokens");
    }

    private CorrectionCommand command() {
        return new CorrectionCommand("SELF_INTRO", "DIRECT_INPUT", null, null, null, "원문", null);
    }

    private String responseJson() {
        String content = """
                {"status":"ok","task_type":"SELF_INTRO_CORRECTION","corrected_text":"개선된 문장","summary":"요약",\
                "changes":[{"before":"원문","after":"개선된 문장","reason":"표현을 구체화했다","evidence_source":"original_text"}],\
                "risk_flags":["근거 확인 필요"],"preserved_meaning":true,"added_facts":[],\
                "recommended_keywords":["문서 정리"],"confidence":0.8}
                """.replace("\n", "");
        return """
                {"model":"claude-haiku-4-5-20251001","content":[{"type":"text","text":%s}],
                 "usage":{"input_tokens":10,"output_tokens":20}}
                """.formatted(jsonString(content));
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
