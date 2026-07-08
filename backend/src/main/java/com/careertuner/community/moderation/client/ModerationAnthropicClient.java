package com.careertuner.community.moderation.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.community.moderation.dto.ModerationImage;
import com.careertuner.interview.service.AnthropicProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 커뮤니티 검열/태그/추출 폴백 provider(Claude Haiku, 2차).
 * 자체 Ollama(gemma4) 실패 시 {@link ModerationLlmGateway} 가 호출한다.
 *
 * <p>{@link OllamaClient} 와 동일한 시그니처(chat(system, user, schema):String)로, 기대 JSON 스키마를
 * 프롬프트에 임베드하고 "순수 JSON 만 출력" 지시로 structured output 을 흉내낸다(Anthropic 은 JSON 모드
 * 플래그가 없다). 파싱은 호출부({@code PostModerationService})가 하므로 여기서는 JSON 텍스트만 돌려준다.
 * 키·모델·baseUrl 은 D 도메인과 공유하는 {@link AnthropicProperties}(careertuner.anthropic)를 재사용한다.
 */
@Component
public class ModerationAnthropicClient {

    private static final int MAX_ATTEMPTS = 3;

    /**
     * Anthropic Messages API 의 이미지당 상한 — <b>base64 인코딩 기준</b> 10MB.
     *
     * <p>base64 는 원본 바이트의 약 4/3 배라, {@code PostImageModerationService.MAX_IMAGE_BYTES}(원본 8MB)를
     * 통과한 이미지도 원본 7.5MB 를 넘으면 여기서 초과한다. 그 구간은 400 이 확정이므로 호출 자체를 건너뛴다
     * ({@link #visionPayloadWithinLimit}) — 400 을 받고 재시도까지 태우는 낭비를 막고 다음 tier 로 넘긴다.
     */
    private static final int MAX_IMAGE_BASE64_BYTES = 10 * 1024 * 1024;

    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ModerationAnthropicClient(AnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    /** 키 설정 여부 — 게이트웨이가 Claude 폴백 시도 가능 여부 판단에 쓴다. */
    public boolean available() {
        return properties.configured();
    }

    /**
     * vision 요청의 모든 이미지가 Anthropic 이미지당 상한(base64 10MB) 안에 드는지.
     * 한 장이라도 넘으면 게이트웨이가 이 tier 를 건너뛴다. base64 는 ASCII 라 문자 수 = 바이트 수.
     */
    public boolean visionPayloadWithinLimit(List<ModerationImage> images) {
        if (images == null) {
            return true;
        }
        for (ModerationImage image : images) {
            String data = image.base64Data();
            if (data != null && data.length() > MAX_IMAGE_BASE64_BYTES) {
                return false;
            }
        }
        return true;
    }

    public String chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        String schemaHint;
        try {
            schemaHint = objectMapper.writeValueAsString(jsonSchema);
        } catch (JacksonException ex) {
            schemaHint = "{}";
        }
        String userWithSchema = userText
                + "\n\n반드시 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력하라. "
                + "코드블록·설명·여는말 없이 순수 JSON 만 출력한다.\nJSON 스키마:\n" + schemaHint;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userWithSchema)));
        body.put("temperature", 0.0);

        return extractText(post(body));
    }

    /**
     * 이미지 검열 vision 폴백 — user 메시지에 image 블록(base64) + text 블록(스키마 지시)을 함께 넣는다.
     * 공고 OCR({@code BAnthropicClient})의 image source 포맷과 동일하다.
     */
    public String chatVision(String systemPrompt, String userText,
                             List<ModerationImage> images, Map<String, Object> jsonSchema) {
        String schemaHint;
        try {
            schemaHint = objectMapper.writeValueAsString(jsonSchema);
        } catch (JacksonException ex) {
            schemaHint = "{}";
        }
        String userWithSchema = userText
                + "\n\n반드시 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력하라. "
                + "코드블록·설명·여는말 없이 순수 JSON 만 출력한다.\nJSON 스키마:\n" + schemaHint;

        List<Object> content = new ArrayList<>();
        for (ModerationImage image : images) {
            content.add(Map.of("type", "image",
                    "source", Map.of("type", "base64", "media_type", image.mediaType(), "data", image.base64Data())));
        }
        content.add(Map.of("type", "text", "text", userWithSchema));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("max_tokens", properties.getMaxTokens());
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("temperature", 0.0);

        return extractText(post(body));
    }

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.messagesUrl()))
                            .timeout(properties.getTimeout())
                            .header("x-api-key", properties.getApiKey())
                            .header("anthropic-version", properties.getVersion())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return objectMapper.readTree(response.body());
                    }
                    if (attempt < MAX_ATTEMPTS && isRetryable(response.statusCode())) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new IllegalStateException("Anthropic 검열 요청 실패: HTTP " + response.statusCode());
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new IllegalStateException("Anthropic 검열 응답 처리 실패", ex);
                }
            }
            throw new IllegalStateException("Anthropic 검열 요청 실패");
        } catch (JacksonException ex) {
            throw new IllegalStateException("Anthropic 검열 요청 구성 실패", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Anthropic 검열 요청 중단됨", ex);
        }
    }

    /** content[*] 의 text 블록을 이어붙이고 코드펜스를 제거해 순수 JSON 텍스트만 돌려준다. */
    private String extractText(JsonNode root) {
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
            throw new IllegalStateException("Anthropic 검열 응답이 비어 있습니다");
        }
        return text;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(attempt * 1000L);
    }
}
