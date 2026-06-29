package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;
import com.careertuner.correction.ai.CorrectionAiClient.CorrectionPayload;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class SelfLlmCorrectionProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("calls the requested Ollama model once and maps the trained output schema")
    void correct_callsRequestedModelAndMapsOutput() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseJson().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        CorrectionAiProperties properties = new CorrectionAiProperties();
        properties.getSelf().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        ObjectMapper objectMapper = new ObjectMapper();
        SelfLlmCorrectionProvider provider = new SelfLlmCorrectionProvider(
                properties, objectMapper, new SelfCorrectionOutputParser(objectMapper));

        CorrectionPayload payload = provider.correct(command(), "careertuner-e-correction:8b", Duration.ofSeconds(2));

        assertThat(payload.improvedText()).isEqualTo("개선된 문장");
        assertThat(payload.issues()).containsExactly("근거 확인 필요");
        assertThat(payload.changeReasons()).containsExactly("표현을 구체화했다");
        assertThat(payload.usage().model()).isEqualTo("careertuner-e-correction:8b");
        assertThat(payload.modelResult()).containsEntry("task_type", "SELF_INTRO_CORRECTION");
        assertThat(requestBody.get()).contains("careertuner-e-correction:8b", "SELF_INTRO_CORRECTION");
    }

    private CorrectionCommand command() {
        return new CorrectionCommand("SELF_INTRO", "DIRECT_INPUT", null, null, null, "원문", null);
    }

    private String responseJson() {
        String content = """
                <think></think>
                {"status":"ok","task_type":"SELF_INTRO_CORRECTION","corrected_text":"개선된 문장","summary":"요약",\
                "changes":[{"before":"원문","after":"개선된 문장","reason":"표현을 구체화했다","evidence_source":"original_text"}],\
                "risk_flags":["근거 확인 필요"],"preserved_meaning":true,"added_facts":[],\
                "recommended_keywords":["문서 정리"],"confidence":0.8}
                """.replace("\n", "");
        return """
                {"model":"careertuner-e-correction:8b","choices":[{"message":{"content":%s}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}
                """.formatted(jsonString(content));
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
