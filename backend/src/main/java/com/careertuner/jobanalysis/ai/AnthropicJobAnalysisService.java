package com.careertuner.jobanalysis.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobanalysis.ai.prompt.JobAnalysisPromptCatalog;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 공고 분석의 Claude(Haiku) 단계 — 폴백 디스패처의 1차 폴백 provider.
 *
 * <p>{@link OssJobAnalysisClient} 와 같은 도메인 스타일(전송+payload 빌드 self-contained)로,
 * Anthropic Messages API 를 호출해 {@link JobAnalysisPayload} 를 만든다. json_schema 강제 모드가 없어
 * 기대 필드를 프롬프트에 명시하고 "순수 JSON 만 출력" 으로 구조화를 유도한다. 키가 없거나 호출이 실패하면
 * 예외를 던지고, 상위 {@link JobAnalysisAiProvider} 가 OpenAI 단계로 폴백한다(자체 mock 폴백을 두지 않는다).
 */
@Slf4j
@Component
public class AnthropicJobAnalysisService implements JobAnalysisAiService {

    private static final int MAX_ATTEMPTS = 3;

    private final JobAnalysisAnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AnthropicJobAnalysisService(JobAnalysisAnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public boolean configured() {
        return properties.configured();
    }

    @Override
    public JobAnalysisPayload analyze(ApplicationCase applicationCase, String sourceText) {
        if (!configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Anthropic API 키가 설정되어 있지 않습니다.");
        }

        String userPrompt = """
                회사명: %s
                직무명: %s

                채용공고:
                %s

                아래 필드를 가진 JSON 객체 하나만 출력하라(코드블록·설명 없이 순수 JSON):
                %s
                requiredSkills·preferredSkills·evidence·ambiguousConditions 는 문자열 배열로 작성한다.
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                sourceText, JobAnalysisPromptCatalog.SCHEMA_SUMMARY);

        JsonNode payload = post(JobAnalysisPromptCatalog.SYSTEM_PROMPT, userPrompt);

        return new JobAnalysisPayload(
                textOrNull(payload, "employmentType"),
                textOrNull(payload, "experienceLevel"),
                arrayJsonOrEmpty(payload, "requiredSkills"),
                arrayJsonOrEmpty(payload, "preferredSkills"),
                textOrNull(payload, "duties"),
                textOrNull(payload, "qualifications"),
                textOrNull(payload, "difficulty"),
                textOrNull(payload, "summary"),
                arrayJsonOrEmpty(payload, "evidence"),
                arrayJsonOrEmpty(payload, "ambiguousConditions"),
                new Usage(properties.getModel(), 0, 0, 0));
    }

    private JsonNode post(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "max_tokens", properties.getMaxTokens(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2);
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.messagesUrl()))
                            .timeout(properties.getTimeout())
                            .header("x-api-key", properties.getApiKey())
                            .header("anthropic-version", properties.getVersion())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return parseContent(objectMapper.readTree(response.body()));
                    }
                    if (attempt < MAX_ATTEMPTS && retryable(response.statusCode())) {
                        sleep(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "Claude 공고 분석 요청 실패 (" + response.statusCode() + ")");
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS) {
                        sleep(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 공고 분석 응답을 처리하지 못했습니다.");
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 공고 분석 요청에 실패했습니다.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 공고 분석 요청을 구성하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 공고 분석 호출이 중단되었습니다.");
        }
    }

    private JsonNode parseContent(JsonNode root) {
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 공고 분석 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Claude 공고 분석 응답이 JSON 형식이 아닙니다.");
        }
    }

    private boolean retryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void sleep(int attempt) throws InterruptedException {
        Thread.sleep(attempt * 1000L);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private String arrayJsonOrEmpty(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value != null && value.isArray()) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JacksonException ex) {
                return "[]";
            }
        }
        return "[]";
    }
}
