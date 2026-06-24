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

@Slf4j
@Component
public class OssJobAnalysisClient implements JobAnalysisAiService {

    private final JobAnalysisAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OssJobAnalysisClient(JobAnalysisAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    @Override
    public JobAnalysisPayload analyze(ApplicationCase applicationCase, String sourceText) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "자체 공고 분석 모델(OSS)이 설정되어 있지 않습니다.");
        }

        String userPrompt = """
                회사명: %s
                직무명: %s

                채용공고:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(), sourceText);

        JsonNode payload = chat(JobAnalysisPromptCatalog.SYSTEM_PROMPT, userPrompt);

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
                ossUsage());
    }

    private JsonNode chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"));
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(chatUrl()))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "자체 공고 분석 모델 요청 실패 (" + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parseJson(content);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("OSS job analysis request failed", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 공고 분석 모델 응답을 처리하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 공고 분석 모델 호출이 중단되었습니다.");
        }
    }

    private JsonNode parseJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 공고 분석 모델 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 공고 분석 모델 응답이 JSON 형식이 아닙니다.");
        }
    }

    private String chatUrl() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        return base + "/chat/completions";
    }

    private Usage ossUsage() {
        return new Usage(properties.getModel(), 0, 0, 0);
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
        if (value.isMissingNode() || value.isNull()) {
            return "[]";
        }
        if (value.isArray()) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JacksonException ex) {
                return "[]";
            }
        }
        return "[]";
    }
}
