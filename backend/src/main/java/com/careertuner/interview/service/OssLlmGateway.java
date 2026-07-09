package com.careertuner.interview.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 자체 파인튜닝 모델(Ollama, OpenAI 호환 {@code /v1/chat/completions}) 전송 게이트웨이.
 *
 * <p>{@link InterviewLlmGateway} 의 oss 구현. 학습된 생성 task(질문생성·모범답안)를 자체 모델로 보내기 위한 통로다.
 * 채점(EVAL)은 별도로 {@link OssAnswerEvaluator}(InterviewEvaluatorProvider 분기)가 담당하므로 여기서는
 * 생성 task 전송에 집중한다. 설정은 같은 자체모델 서버라 채점기와 {@link InterviewEvalProperties}
 * (careertuner.interview.eval.base-url/model)를 공유한다.
 *
 * <p>구조화 출력은 기대 스키마를 프롬프트에 임베드 + {@code json_object} 모드로 보장한다(소형 모델 방어,
 * {@link AnthropicLlmGateway} 와 동일 전략). base-url 미설정 시 {@link #available()} 가 false 이고,
 * 상위 {@link FallbackInterviewLlmGateway} 가 Claude/OpenAI 로 폴백한다.
 *
 * <p>최종적으로 자체 모델이 전 task 를 커버하면 Claude 게이트웨이는 폐기되고 이 게이트웨이가 1차가 된다(단계적 교체).
 *
 * <p>참고: HTTP 호출부는 {@link OssAnswerEvaluator} 와 거의 동일하다(같은 Ollama /v1 서버). 추후 공통 transport 로
 * 추출할 수 있으나, 채점 경로를 건드리지 않기 위해 현재는 독립 유지한다.
 */
@Component
public class OssLlmGateway implements InterviewLlmGateway {

    private final InterviewEvalProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GpuPermitGate gpuPermitGate;

    public OssLlmGateway(InterviewEvalProperties properties, ObjectMapper objectMapper,
                         GpuPermitGate gpuPermitGate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.gpuPermitGate = gpuPermitGate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    /** base-url 설정 여부 — 폴백 디스패처가 oss 시도 가능 여부 판단에 쓴다. */
    public boolean available() {
        return properties.configured();
    }

    @Override
    public Result complete(Request request) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "자체 모델(OSS) base-url 이 설정되어 있지 않습니다.");
        }
        return new Result(chat(request), usage());
    }

    private JsonNode chat(Request request) {
        String schemaHint;
        try {
            schemaHint = objectMapper.writeValueAsString(request.jsonSchema());
        } catch (JacksonException ex) {
            schemaHint = "{}";
        }
        String userText = request.userPrompt()
                + "\n\n반드시 아래 JSON 스키마를 만족하는 JSON 객체 하나만 출력하라. "
                + "코드블록·설명 없이 순수 JSON 만 출력한다.\nJSON 스키마:\n" + schemaHint;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", userText)));
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));

        try {
            // 단건 호출이라 총 시간예산은 요청 타임아웃 절삭이 전부다(예산 0 = 무제한 = 기존 동작).
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(chatUrl()))
                    .timeout(AiTotalTimeBudget.start(properties.getTotalTimeBudget()).cap(properties.getTimeout()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + properties.getApiKey());
            }
            HttpResponse<String> response;
            try (GpuPermitGate.GpuPermit permit = gpuPermitGate.acquire("interview")) {
                response = httpClient.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "자체 모델 요청 실패 (" + response.statusCode() + ")");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parseJson(content);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 모델 응답을 처리하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 모델 호출이 중단되었습니다.");
        }
    }

    private JsonNode parseJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        // 소형 모델이 JSON 앞뒤에 잡설(예: "JSON 출력:")을 붙이는 경우 — 첫 {/[ ~ 마지막 }/] 만 남긴다.
        text = extractJsonSpan(text);
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 모델 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 모델 응답이 JSON 형식이 아닙니다.");
        }
    }

    /** 소형 모델이 JSON 앞뒤에 붙이는 잡설을 제거 — 첫 {/[ 부터 마지막 }/] 까지만 취한다(채점기와 공용). */
    static String extractJsonSpan(String text) {
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start = objStart < 0 ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }

    private String chatUrl() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        return base + "/chat/completions";
    }

    private InterviewOpenAiClient.Usage usage() {
        // 자체 서버는 과금 토큰 집계 대상이 아니므로 0 으로 기록한다(모델 id 만 남긴다).
        return new InterviewOpenAiClient.Usage(properties.getModel(), 0, 0, 0);
    }
}
