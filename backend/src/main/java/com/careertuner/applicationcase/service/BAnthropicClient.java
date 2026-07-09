package com.careertuner.applicationcase.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * B 공고/회사 분석의 Claude(Haiku) 호출 어댑터 — {@link BLocalLlmClient} 의 형제.
 *
 * <p>{@code chat(system, user, schema)} 가 자체모델(Ollama) 클라이언트와 같은 "JSON 문자열 content" 를
 * 반환하므로, {@link BAnalysisGenerationService} 의 기존 파싱/검증 로직을 그대로 재사용할 수 있다.
 * Anthropic 은 구조화 출력 강제 모드가 없어 기대 스키마를 프롬프트에 임베드하고 순수 JSON 만 요구하며,
 * 응답이 코드블록을 섞어도 펜스를 제거해 반환한다. 실패 시 예외를 던져 상위 디스패처가 OpenAI 로 폴백한다.
 */
@Component
@Slf4j
public class BAnthropicClient {

    private static final int MAX_ATTEMPTS = 3;

    private final BAnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BAnthropicClient(BAnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    public String model() {
        return properties.getModel();
    }

    public String chat(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        if (!configured()) {
            throw new IllegalStateException("Anthropic API key is not configured.");
        }

        String schemaHint;
        try {
            schemaHint = objectMapper.writeValueAsString(jsonSchema);
        } catch (JacksonException ex) {
            schemaHint = "{}";
        }
        String userText = userPrompt
                + "\n\n반드시 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력하라. "
                + "코드블록·설명·여는말 없이 순수 JSON 만 출력한다.\nJSON 스키마:\n" + schemaHint;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userText)));
        body.put("temperature", 0);

        JsonNode root = post(body);
        return extractContent(root);
    }

    /**
     * 이미지에서 공고문 텍스트를 추출한다(Claude Vision OCR). 공고 이미지/스캔본을 글자로 옮긴다.
     * 실패 시 예외를 던져 상위({@code JobPostingTextExtractor})가 OpenAI Vision 으로 폴백한다.
     */
    public OcrPayload extractImageText(String contentType, byte[] bytes) {
        if (!configured()) {
            throw new IllegalStateException("Anthropic API key is not configured.");
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> imageBlock = Map.of("type", "image",
                "source", Map.of("type", "base64", "media_type", contentType, "data", base64));
        Map<String, Object> textBlock = Map.of("type", "text",
                "text", "이미지 안의 채용공고 텍스트만 한국어 원문에 가깝게 추출하라. 설명·요약·여는말 없이 본문 텍스트만 반환한다.");
        return visionExtract(List.of(imageBlock, textBlock));
    }

    /**
     * 스캔/이미지 PDF에서 공고문 텍스트를 추출한다(Claude Vision OCR).
     * Anthropic document 입력은 200K 컨텍스트 모델 기준 최대 100페이지·32MB(공고문엔 충분).
     * 실패 시 예외를 던져 상위가 OpenAI Vision 으로 폴백한다.
     */
    public OcrPayload extractPdfText(byte[] bytes) {
        if (!configured()) {
            throw new IllegalStateException("Anthropic API key is not configured.");
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> documentBlock = Map.of("type", "document",
                "source", Map.of("type", "base64", "media_type", "application/pdf", "data", base64));
        Map<String, Object> textBlock = Map.of("type", "text",
                "text", "PDF 모든 페이지에서 채용공고 텍스트만 추출하라. 설명·요약 없이 읽힌 본문 텍스트만 반환한다.");
        return visionExtract(List.of(documentBlock, textBlock));
    }

    private OcrPayload visionExtract(List<Map<String, Object>> content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("temperature", 0);
        JsonNode root = post(body);
        return new OcrPayload(extractPlainText(root), "claude", properties.getModel(), anthropicUsage(root));
    }

    /** Anthropic 응답 usage(input_tokens/output_tokens) → 공통 AiUsage. Claude OCR usage 기록용. */
    private AiUsage anthropicUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int in = usage.path("input_tokens").asInt(0);
        int out = usage.path("output_tokens").asInt(0);
        return new AiUsage(properties.getModel(), in, out, in + out);
    }

    /** OCR 응답용 — chat 과 달리 코드펜스를 벗기지 않고 text 블록을 그대로 이어붙인다(원문에 ``` 가 있을 수 있음). */
    private String extractPlainText(JsonNode root) {
        StringBuilder builder = new StringBuilder();
        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText(""))) {
                    builder.append(block.path("text").asText(""));
                }
            }
        }
        String text = builder.toString().trim();
        if (text.isBlank()) {
            throw new IllegalStateException("Anthropic vision response content is empty.");
        }
        return text;
    }

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.messagesUrl()))
                            .timeout(properties.getTimeout())
                            .header("x-api-key", properties.getApiKey())
                            .header("anthropic-version", properties.getVersion())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return objectMapper.readTree(response.body());
                    }
                    if (attempt < MAX_ATTEMPTS && retryable(response.statusCode())) {
                        sleep(attempt);
                        continue;
                    }
                    throw new IllegalStateException("Anthropic request failed: HTTP " + response.statusCode());
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS) {
                        sleep(attempt);
                        continue;
                    }
                    throw new IllegalStateException("Anthropic response could not be processed.", ex);
                }
            }
            throw new IllegalStateException("Anthropic request failed.");
        } catch (JacksonException ex) {
            throw new IllegalStateException("Anthropic request could not be built.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Anthropic request was interrupted.", ex);
        }
    }

    private String extractContent(JsonNode root) {
        StringBuilder builder = new StringBuilder();
        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText(""))) {
                    builder.append(block.path("text").asText(""));
                }
            }
        }
        String text = builder.toString().trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new IllegalStateException("Anthropic response content is empty.");
        }
        return text;
    }

    private boolean retryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void sleep(int attempt) throws InterruptedException {
        Thread.sleep(attempt * 1000L);
    }
}
