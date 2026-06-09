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
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobanalysis.ai.prompt.JobAnalysisPromptCatalog;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OpenAiResponsesClient {

    private static final int MAX_ATTEMPTS = 3;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiResponsesClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public JobAnalysisPayload analyzeJobPosting(ApplicationCase applicationCase, String postingText) {
        JsonNode root = post(structuredRequest(
                "job_analysis",
                jobAnalysisSchema(),
                JobAnalysisPromptCatalog.SYSTEM_PROMPT,
                """
                회사명: %s
                직무명: %s

                채용공고:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(), postingText)));
        JsonNode payload = parseOutputJson(root);
        Usage usage = usage(root);
        return new JobAnalysisPayload(
                text(payload, "employmentType"),
                text(payload, "experienceLevel"),
                arrayJson(payload, "requiredSkills"),
                arrayJson(payload, "preferredSkills"),
                text(payload, "duties"),
                text(payload, "qualifications"),
                normalizeDifficulty(text(payload, "difficulty")),
                text(payload, "summary"),
                usage);
    }

    public CompanyAnalysisPayload analyzeCompany(ApplicationCase applicationCase, String postingText) {
        JsonNode root = post(structuredRequest(
                "company_analysis",
                companyAnalysisSchema(),
                CompanyAnalysisPromptCatalog.SYSTEM_PROMPT,
                """
                회사명: %s
                직무명: %s

                채용공고:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(), postingText)));
        JsonNode payload = parseOutputJson(root);
        Usage usage = usage(root);
        return new CompanyAnalysisPayload(
                text(payload, "companySummary"),
                text(payload, "recentIssues"),
                text(payload, "industry"),
                arrayJson(payload, "competitors"),
                text(payload, "interviewPoints"),
                arrayJson(payload, "sources"),
                usage);
    }

    public TextPayload extractImageText(String contentType, byte[] bytes) {
        String dataUrl = "data:%s;base64,%s".formatted(contentType, Base64.getEncoder().encodeToString(bytes));
        JsonNode root = post(textRequest(List.of(
                inputText("이미지 안에 있는 채용공고 텍스트만 한국어 원문에 가깝게 추출해줘. 설명이나 요약은 쓰지 마."),
                inputImage(dataUrl))));
        return new TextPayload(cleanOutputText(root), usage(root));
    }

    public TextPayload extractPdfText(String filename, byte[] bytes) {
        String fileData = "data:application/pdf;base64,%s".formatted(Base64.getEncoder().encodeToString(bytes));
        JsonNode root = post(textRequest(List.of(
                inputFile(filename, fileData),
                inputText("PDF의 모든 페이지에서 채용공고 텍스트만 추출해줘. 설명이나 요약은 쓰지 말고, 읽힌 텍스트만 반환해."))));
        return new TextPayload(cleanOutputText(root), usage(root));
    }

    private JsonNode post(Map<String, Object> requestBody) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI API 키가 설정되어 있지 않습니다.");
        }
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.responsesUrl()))
                            .timeout(properties.getTimeout())
                            .header("Authorization", "Bearer " + properties.getApiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return objectMapper.readTree(response.body());
                    }

                    String message = errorMessage(response.body());
                    if (attempt < MAX_ATTEMPTS && isRetryable(response.statusCode(), message)) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "OpenAI 요청에 실패했습니다. " + truncate(message, 300));
                } catch (IOException ex) {
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 응답을 처리하지 못했습니다.");
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 요청에 실패했습니다.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 요청을 구성하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 요청이 중단되었습니다.");
        }
    }

    private Map<String, Object> structuredRequest(String name, Map<String, Object> schema, String systemPrompt, String userPrompt) {
        Map<String, Object> body = baseBody();
        body.put("input", List.of(
                message("system", List.of(inputText(systemPrompt))),
                message("user", List.of(inputText(userPrompt)))));
        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", name,
                        "strict", true,
                        "schema", schema)));
        return body;
    }

    private Map<String, Object> textRequest(List<Map<String, Object>> content) {
        Map<String, Object> body = baseBody();
        body.put("input", List.of(message("user", content)));
        return body;
    }

    private Map<String, Object> baseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        return body;
    }

    private Map<String, Object> message(String role, List<Map<String, Object>> content) {
        return Map.of("role", role, "content", content);
    }

    private Map<String, Object> inputText(String text) {
        return Map.of("type", "input_text", "text", text);
    }

    private Map<String, Object> inputImage(String imageUrl) {
        return Map.of("type", "input_image", "image_url", imageUrl, "detail", "high");
    }

    private Map<String, Object> inputFile(String filename, String fileData) {
        return Map.of("type", "input_file", "filename", filename, "file_data", fileData);
    }

    private JsonNode parseOutputJson(JsonNode root) {
        String text = cleanOutputText(root);
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 분석 결과가 JSON 형식이 아닙니다.");
        }
    }

    private String cleanOutputText(JsonNode root) {
        String text = outputText(root).trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 응답 본문이 비어 있습니다.");
        }
        return text;
    }

    private String outputText(JsonNode root) {
        String direct = root.path("output_text").asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        StringBuilder builder = new StringBuilder();
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            builder.append(text);
                        }
                    }
                }
            }
        }
        return builder.toString();
    }

    private Usage usage(JsonNode root) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        return new Usage(properties.getModel(), inputTokens, outputTokens, totalTokens);
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private String arrayJson(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return "[]";
        }
    }

    private String normalizeDifficulty(String value) {
        return switch (value) {
            case "EASY", "NORMAL", "HARD" -> value;
            default -> "NORMAL";
        };
    }

    private Map<String, Object> jobAnalysisSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("employmentType", stringSchema());
        properties.put("experienceLevel", stringSchema());
        properties.put("requiredSkills", stringArraySchema());
        properties.put("preferredSkills", stringArraySchema());
        properties.put("duties", stringSchema());
        properties.put("qualifications", stringSchema());
        properties.put("difficulty", Map.of("type", "string", "enum", List.of("EASY", "NORMAL", "HARD")));
        properties.put("summary", stringSchema());
        return objectSchema(properties, List.of(
                "employmentType", "experienceLevel", "requiredSkills", "preferredSkills",
                "duties", "qualifications", "difficulty", "summary"));
    }

    private Map<String, Object> companyAnalysisSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("companySummary", stringSchema());
        properties.put("recentIssues", stringSchema());
        properties.put("industry", stringSchema());
        properties.put("competitors", stringArraySchema());
        properties.put("interviewPoints", stringSchema());
        properties.put("sources", stringArraySchema());
        return objectSchema(properties, List.of(
                "companySummary", "recentIssues", "industry", "competitors", "interviewPoints", "sources"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private String errorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText("");
            return message.isBlank() ? body : message;
        } catch (JacksonException ex) {
            return body;
        }
    }

    private boolean isRetryable(int statusCode, String message) {
        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            return true;
        }
        String lowerMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lowerMessage.contains("timeout")
                || lowerMessage.contains("upstream connect")
                || lowerMessage.contains("disconnect/reset");
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(attempt * 1000L);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    public record Usage(String model, int inputTokens, int outputTokens, int totalTokens) {
    }

    public record TextPayload(String text, Usage usage) {
    }

    public record JobAnalysisPayload(
            String employmentType,
            String experienceLevel,
            String requiredSkills,
            String preferredSkills,
            String duties,
            String qualifications,
            String difficulty,
            String summary,
            Usage usage
    ) {
    }

    public record CompanyAnalysisPayload(
            String companySummary,
            String recentIssues,
            String industry,
            String competitors,
            String interviewPoints,
            String sources,
            Usage usage
    ) {
    }
}
