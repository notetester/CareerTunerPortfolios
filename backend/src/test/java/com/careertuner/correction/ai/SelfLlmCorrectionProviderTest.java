package com.careertuner.correction.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.gpu.GpuPermitGate;
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
    @DisplayName("calls the configured 3B model once and maps the trained output schema")
    void correct_callsRequestedModelAndMapsOutput() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        SelfLlmCorrectionProvider provider = provider(responseJson(true), requestBody);

        CorrectionPayload payload = provider.correct(
                command(), "careertuner-e-correction-3b:latest", Duration.ofSeconds(2));

        assertThat(payload.improvedText()).isEqualTo("개선된 문장");
        assertThat(payload.issues()).containsExactly("근거 확인 필요");
        assertThat(payload.changeReasons()).containsExactly(
                "표현을 구체화했다", "문장을 명확히 했다", "근거를 유지했다");
        assertThat(payload.usage().model()).isEqualTo("careertuner-e-correction-3b:latest");
        assertThat(payload.modelResult()).containsEntry("task_type", "SELF_INTRO_CORRECTION");
        assertThat(requestBody.get()).contains(
                "careertuner-e-correction-3b:latest",
                "SELF_INTRO_CORRECTION",
                "\"max_tokens\":3072",
                "\"response_format\":{\"type\":\"json_object\"}");
    }

    @Test
    @DisplayName("rejects a 3B response when preserved_meaning is false and retains it for repair")
    void correct_rejectsOutputWhenMeaningIsNotPreserved() throws IOException {
        SelfLlmCorrectionProvider provider = provider(responseJson(false), new AtomicReference<>());

        SelfCorrectionOutputParser.InvalidOutputException failure = catchThrowableOfType(
                () -> provider.correct(
                        command(), "careertuner-e-correction-3b:latest", Duration.ofSeconds(2)),
                SelfCorrectionOutputParser.InvalidOutputException.class);

        assertThat(failure).hasMessageContaining("preserved_meaning must be true");
        assertThat(failure.previousOutput()).contains("\"preserved_meaning\":false");
    }

    @Test
    @DisplayName("repair request includes the previous output, validation error, and deterministic temperature")
    void correct_buildsRepairRequest() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        SelfLlmCorrectionProvider provider = provider(responseJson(true), requestBody);

        CorrectionPayload payload = provider.correct(
                command(),
                "careertuner-e-correction-3b:latest",
                Duration.ofSeconds(2),
                new SelfLlmCorrectionProvider.RepairContext(
                        "root is missing changes",
                        "{\"status\":\"ok\"}"));

        assertThat(payload.improvedText()).isEqualTo("개선된 문장");
        assertThat(requestBody.get()).contains(
                "root is missing changes",
                "이전 응답이 출력 계약 검증에 실패했다",
                "{\\\"status\\\":\\\"ok\\\"}",
                "\"temperature\":0.0");
    }

    @Test
    @DisplayName("rejects a corrected text shorter than the request minimum")
    void correct_rejectsShortOutput() throws IOException {
        SelfLlmCorrectionProvider provider = provider(
                responseJson(true, "짧음"), new AtomicReference<>());
        SelfCorrectionInput input = new SelfCorrectionInput(
                "sample", "SELF_INTRO_CORRECTION", "원문".repeat(20), "직무",
                Map.of(), List.of(), Map.of("min_chars", 20, "max_chars", 100));

        SelfCorrectionOutputParser.InvalidOutputException failure = catchThrowableOfType(
                () -> provider.correct(command(input), "careertuner-e-correction-3b:latest", Duration.ofSeconds(2)),
                SelfCorrectionOutputParser.InvalidOutputException.class);

        assertThat(failure).hasMessageContaining("shorter than min_chars 20");
    }

    @Test
    @DisplayName("fills a repair response that is at most three characters below the minimum")
    void correct_restoresMinorLengthShortfallAfterRepair() throws IOException {
        SelfLlmCorrectionProvider provider = provider(
                responseJson(true, "123456789"), new AtomicReference<>());
        SelfCorrectionInput input = new SelfCorrectionInput(
                "sample", "SELF_INTRO_CORRECTION", "원문", "직무",
                Map.of(), List.of(), Map.of("min_chars", 10, "max_chars", 12));

        CorrectionPayload payload = provider.correct(
                command(input),
                "careertuner-e-correction-3b:latest",
                Duration.ofSeconds(2),
                new SelfLlmCorrectionProvider.RepairContext(
                        "corrected_text exceeds max_chars 12.",
                        "{}"));

        assertThat(payload.improvedText()).isEqualTo("123456789.");
    }

    @Test
    @DisplayName("keeps rejecting a repair response more than three characters below the minimum")
    void correct_rejectsLargeLengthShortfallAfterRepair() throws IOException {
        SelfLlmCorrectionProvider provider = provider(
                responseJson(true, "123456"), new AtomicReference<>());
        SelfCorrectionInput input = new SelfCorrectionInput(
                "sample", "SELF_INTRO_CORRECTION", "원문", "직무",
                Map.of(), List.of(), Map.of("min_chars", 10, "max_chars", 12));

        SelfCorrectionOutputParser.InvalidOutputException failure = catchThrowableOfType(
                () -> provider.correct(
                        command(input),
                        "careertuner-e-correction-3b:latest",
                        Duration.ofSeconds(2),
                        new SelfLlmCorrectionProvider.RepairContext(
                                "corrected_text is shorter than min_chars 10.",
                                "{}")),
                SelfCorrectionOutputParser.InvalidOutputException.class);

        assertThat(failure).hasMessageContaining("shorter than min_chars 10");
    }

    @Test
    @DisplayName("rejects collapsed paragraphs when paragraph preservation is required")
    void correct_rejectsCollapsedParagraphs() throws IOException {
        SelfLlmCorrectionProvider provider = provider(
                responseJson(true, "한 문단으로 합쳤습니다"), new AtomicReference<>());
        SelfCorrectionInput input = new SelfCorrectionInput(
                "sample", "SELF_INTRO_CORRECTION", "첫 문단\n\n둘째 문단", "직무",
                Map.of(), List.of(), Map.of(
                        "min_chars", 1,
                        "max_chars", 100,
                        "preserve_paragraphs", true));

        SelfCorrectionOutputParser.InvalidOutputException failure = catchThrowableOfType(
                () -> provider.correct(command(input), "careertuner-e-correction-3b:latest", Duration.ofSeconds(2)),
                SelfCorrectionOutputParser.InvalidOutputException.class);

        assertThat(failure).hasMessageContaining("preserve at least 2 paragraphs");
    }

    @Test
    @DisplayName("restores paragraph boundaries after a paragraph repair response")
    void correct_restoresParagraphsAfterRepair() throws IOException {
        SelfLlmCorrectionProvider provider = provider(
                responseJson(true, "첫 문장입니다. 둘째 문장입니다. 셋째 문장입니다."), new AtomicReference<>());
        SelfCorrectionInput input = new SelfCorrectionInput(
                "sample", "SELF_INTRO_CORRECTION", "첫 문단\n\n둘째 문단\n\n셋째 문단", "직무",
                Map.of(), List.of(), Map.of(
                        "min_chars", 1,
                        "max_chars", 100,
                        "preserve_paragraphs", true));

        CorrectionPayload payload = provider.correct(
                command(input),
                "careertuner-e-correction-3b:latest",
                Duration.ofSeconds(2),
                new SelfLlmCorrectionProvider.RepairContext(
                        "corrected_text must preserve at least 3 paragraphs.",
                        "{}"));

        assertThat(payload.improvedText()).isEqualTo("첫 문장입니다.\n\n둘째 문장입니다.\n\n셋째 문장입니다.");
    }

    @Test
    @DisplayName("keeps rejecting a paragraph repair when sentence boundaries are insufficient")
    void correct_rejectsUnrestorableParagraphRepair() throws IOException {
        SelfLlmCorrectionProvider provider = provider(
                responseJson(true, "첫 문장입니다. 둘째 문장입니다."), new AtomicReference<>());
        SelfCorrectionInput input = new SelfCorrectionInput(
                "sample", "SELF_INTRO_CORRECTION", "첫 문단\n\n둘째 문단\n\n셋째 문단", "직무",
                Map.of(), List.of(), Map.of(
                        "min_chars", 1,
                        "max_chars", 100,
                        "preserve_paragraphs", true));

        SelfCorrectionOutputParser.InvalidOutputException failure = catchThrowableOfType(
                () -> provider.correct(
                        command(input),
                        "careertuner-e-correction-3b:latest",
                        Duration.ofSeconds(2),
                        new SelfLlmCorrectionProvider.RepairContext(
                                "corrected_text must preserve at least 3 paragraphs.",
                                "{}")),
                SelfCorrectionOutputParser.InvalidOutputException.class);

        assertThat(failure).hasMessageContaining("preserve at least 3 paragraphs");
    }

    private SelfLlmCorrectionProvider provider(String responseBody, AtomicReference<String> requestBody)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        CorrectionAiProperties properties = new CorrectionAiProperties();
        properties.getSelf().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        ObjectMapper objectMapper = new ObjectMapper();
        return new SelfLlmCorrectionProvider(
                properties, objectMapper, new SelfCorrectionOutputParser(objectMapper), GpuPermitGate.disabled());
    }

    private CorrectionCommand command() {
        return new CorrectionCommand("SELF_INTRO", "DIRECT_INPUT", null, null, null, "원문", null);
    }

    private CorrectionCommand command(SelfCorrectionInput input) {
        return new CorrectionCommand(
                "SELF_INTRO", "DIRECT_INPUT", null, null, null,
                input.originalText(), null, input);
    }

    private String responseJson(boolean preservedMeaning) {
        return responseJson(preservedMeaning, "개선된 문장");
    }

    private String responseJson(boolean preservedMeaning, String correctedText) {
        String escapedCorrectedText = correctedText.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
        String content = """
                <think></think>
                {"status":"ok","task_type":"SELF_INTRO_CORRECTION","corrected_text":"%s","summary":"요약",\
                "changes":[\
                {"before":"원문","after":"개선된 문장","reason":"표현을 구체화했다","evidence_source":"original_text"},\
                {"before":"원문","after":"개선된 문장","reason":"문장을 명확히 했다","evidence_source":"original_text"},\
                {"before":"원문","after":"개선된 문장","reason":"근거를 유지했다","evidence_source":"original_text"}],\
                "risk_flags":["근거 확인 필요"],"preserved_meaning":%s,"added_facts":[],\
                "recommended_keywords":["문서 정리"],"confidence":0.8}
                """.formatted(escapedCorrectedText, preservedMeaning).replace("\n", "");
        return """
                {"model":"careertuner-e-correction-3b:latest","choices":[{"message":{"content":%s}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}
                """.formatted(jsonString(content));
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
