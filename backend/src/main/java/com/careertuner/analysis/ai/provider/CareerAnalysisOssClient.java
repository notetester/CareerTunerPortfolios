package com.careertuner.analysis.ai.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * C 자체 파인튜닝 모델(Ollama, OpenAI 호환 {@code /v1/chat/completions}) 호출 클라이언트.
 *
 * <p>D 의 {@code interview.service.OssLlmGateway} 패턴을 C 도메인으로 미러링했다(D 파일은 수정하지 않음).
 * 모델({@code careertuner-c-career-strategy-3b})은 <b>설명 텍스트만</b> 생성하도록 학습됐다 — 점수/판단은
 * 서버 규칙엔진(Mock)이 계산해 입력으로 준다(뉴로-심볼릭). 따라서 이 클라이언트는 설명 JSON 만 받아온다.
 *
 * <p>base-url 미설정 시 {@link #available()} 가 false 이고 상위 폴백이 OpenAI/Mock 으로 전환한다.
 * 소형 모델 방어: {@code response_format=json_object} + 앞뒤 잡설 제거({@link #extractJsonSpan}).
 */
@Service
public class CareerAnalysisOssClient {

    private final CareerAnalysisAiProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CareerAnalysisOssClient(CareerAnalysisAiProviderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getOss().getTimeout())
                .build();
    }

    /** base-url 설정 여부 — 폴백 디스패처가 OSS 시도 가능 여부 판단에 쓴다. */
    public boolean available() {
        return properties.getOss().configured();
    }

    /**
     * 적합도 설명 생성 호출. 설명 JSON(fitSummary/strengths/risks/strategyActions/learningTaskReasons)을 반환한다.
     * 실패(미설정/HTTP/타임아웃/JSON 파싱)는 {@link BusinessException} 으로 던져 상위 폴백을 유도한다.
     */
    public JsonNode requestFitExplain(String systemPrompt, String userPrompt) {
        CareerAnalysisAiProviderProperties.Oss oss = properties.getOss();
        if (!oss.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델(OSS) base-url 이 설정되어 있지 않습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", oss.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("temperature", oss.getTemperature());
        // 출력 truncation 방지(설명이 길다 → 최소 1024). Ollama 는 max_tokens 를 num_predict 로 매핑한다.
        body.put("max_tokens", oss.getMaxTokens());
        body.put("response_format", Map.of("type", "json_object"));

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(chatUrl()))
                    .timeout(oss.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (oss.getApiKey() != null && !oss.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + oss.getApiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "C 자체모델 요청 실패 (" + response.statusCode() + ")");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parseJson(content);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 응답을 처리하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 호출이 중단되었습니다.");
        }
    }

    private JsonNode parseJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        text = extractJsonSpan(text);
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "C 자체모델 응답이 JSON 형식이 아닙니다.");
        }
    }

    /** 소형 모델이 JSON 앞뒤에 붙이는 잡설을 제거 — 첫 {/[ 부터 마지막 }/] 까지만 취한다(D 패턴 동일). */
    static String extractJsonSpan(String text) {
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start = objStart < 0 ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }

    private String chatUrl() {
        String base = properties.getOss().getBaseUrl().replaceAll("/+$", "");
        return base + "/chat/completions";
    }
}
