package com.careertuner.applicationcase.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.companyanalysis.ai.prompt.CompanyAnalysisPromptCatalog;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobanalysis.ai.prompt.JobAnalysisPromptCatalog;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class OpenAiResponsesClient {

    private static final int MAX_ATTEMPTS = 3;
    // OCR 하드닝: gpt-4o 가 이미지/PDF 를 때때로 "추출이 지원되지 않는다"류로 거부(짧은 응답)하는 비결정성 대응.
    // 결과가 임계치 미만(거부 추정)이면 1회 재시도한다.
    private static final int OCR_MAX_ATTEMPTS = 2;
    private static final int OCR_MIN_USEFUL_CHARS = 100;
    private static final String OCR_SYSTEM_PROMPT = """
            You are a precise OCR transcription engine for Korean recruitment job postings.
            Transcribe ALL text visible in the document verbatim, preserving the original language and reading order.
            Output ONLY the transcribed text. Do NOT add explanations, apologies, disclaimers, notes, or markdown code fences.
            Never say that you cannot read the file or that text extraction is unsupported — always output whatever text is legible.
            """;

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

    /** OpenAI 키 설정 여부 — 폴백 디스패처가 OpenAI 단계 시도 가능 여부 판단에 쓴다. */
    public boolean configured() {
        return properties.configured();
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
                json(payload, "evidence", "[]"),
                json(payload, "ambiguousConditions", "[]"),
                usage);
    }

    public CompanyAnalysisPayload analyzeCompany(ApplicationCase applicationCase, String postingText) {
        return analyzeCompany(applicationCase, postingText, null);
    }

    /**
     * 기업분석 OpenAI 호출. {@code modelOverride} 가 비어 있지 않으면 이 호출에만 그 모델을 쓰고,
     * 비어 있으면 공용 {@code careertuner.openai.model} 을 쓴다(다른 OpenAI 호출은 영향받지 않음).
     */
    public CompanyAnalysisPayload analyzeCompany(ApplicationCase applicationCase, String postingText,
                                                 String modelOverride) {
        String model = resolveModel(modelOverride);
        JsonNode root = post(structuredRequest(
                "company_analysis",
                companyAnalysisSchema(),
                CompanyAnalysisPromptCatalog.HOSTED_SYSTEM_PROMPT,
                """
                회사명: %s
                직무명: %s

                채용공고:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(), postingText),
                model));
        JsonNode payload = parseOutputJson(root);
        Usage usage = usage(root, model);
        return new CompanyAnalysisPayload(
                text(payload, "companySummary"),
                text(payload, "recentIssues"),
                text(payload, "industry"),
                arrayJson(payload, "competitors"),
                text(payload, "interviewPoints"),
                arrayJson(payload, "sources"),
                json(payload, "verifiedFacts", "[]"),
                json(payload, "aiInferences", "[]"),
                json(payload, "unknowns", "[]"),
                usage);
    }

    public JobPostingMetadataPayload extractJobPostingMetadata(String postingText) {
        JsonNode root = post(structuredRequest(
                "job_posting_metadata",
                jobPostingMetadataSchema(),
                """
                Extract metadata from a job posting.
                Return companyName and jobTitle as concise strings when visible.
                Do not extract postingDate; always return null for postingDate.
                Extract only the final application deadline as deadlineDate when it can be expressed as ISO yyyy-MM-dd.
                For sections labeled 접수기간, 지원기간, 제출기한, 마감일, or similar, use the end date as deadlineDate.
                Use null for deadlineDate when it is missing, relative, ambiguous, or impossible to infer safely.
                Do not guess.
                """,
                """
                Job posting text:
                %s
                """.formatted(postingText)));
        return parseJobPostingMetadataPayload(parseOutputJson(root), usage(root));
    }

    public TextPayload extractImageText(String contentType, byte[] bytes) {
        String dataUrl = "data:%s;base64,%s".formatted(contentType, Base64.getEncoder().encodeToString(bytes));
        return ocrWithRetry(() -> textRequest(OCR_SYSTEM_PROMPT, List.of(
                inputText("이미지 안에 있는 채용공고 텍스트를 원문 그대로 모두 추출해. 설명·요약·사과·안내 문구는 붙이지 말고 추출된 텍스트만 출력해."),
                inputImage(dataUrl))));
    }

    public TextPayload extractPdfText(String filename, byte[] bytes) {
        String fileData = "data:application/pdf;base64,%s".formatted(Base64.getEncoder().encodeToString(bytes));
        return ocrWithRetry(() -> textRequest(OCR_SYSTEM_PROMPT, List.of(
                inputFile(filename, fileData),
                inputText("이 PDF의 모든 페이지에서 채용공고 텍스트를 원문 그대로 모두 추출해. "
                        + "'추출이 지원되지 않는다' 같은 안내나 설명·요약은 붙이지 말고, 읽힌 텍스트만 출력해."))));
    }

    /**
     * OCR 전용 재시도: gpt-4o 가 같은 파일을 어떤 때는 거부(짧은 응답)하고 어떤 때는 정상 추출하는 비결정성 대응.
     * 결과가 {@link #OCR_MIN_USEFUL_CHARS} 미만이면(거부 추정) 최대 {@link #OCR_MAX_ATTEMPTS} 회까지 재요청한다.
     * 최종 시도까지도 임계치 미만이면 <b>빈 문자열</b>을 반환한다 — 상위 {@code ocrFallback} 이 이를 "빈 결과"로 보고
     * 다음 단계(OCR 워커)로 내려가게 하기 위함이다. 짧은 거부 응답을 non-blank 로 반환하면 워커 폴백을 가로막는다.
     */
    private TextPayload ocrWithRetry(Supplier<Map<String, Object>> requestSupplier) {
        TextPayload last = new TextPayload("", null);
        for (int attempt = 1; attempt <= OCR_MAX_ATTEMPTS; attempt++) {
            JsonNode root = post(requestSupplier.get());
            String text = cleanOcrText(root);
            last = new TextPayload(text, usage(root));
            if (text.strip().length() >= OCR_MIN_USEFUL_CHARS) {
                return last;
            }
            if (attempt < OCR_MAX_ATTEMPTS) {
                log.warn("OpenAI OCR 결과가 너무 짧음({}자, 거부 추정) → 재시도 {}/{}",
                        text.strip().length(), attempt + 1, OCR_MAX_ATTEMPTS);
            }
        }
        log.warn("OpenAI OCR 최종 결과가 임계치 미만({}자) → 빈 결과로 반환(상위 워커 폴백에 위임)",
                last.text() == null ? 0 : last.text().strip().length());
        return new TextPayload("", last.usage());
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
                        return parseResponseBody(response.body());
                    }

                    String message = errorMessage(response.body());
                    if (attempt < MAX_ATTEMPTS && isRetryable(response.statusCode(), message)) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "OpenAI 요청에 실패했습니다. " + truncate(message, 300));
                } catch (IOException ex) {
                    log.warn("OpenAI request I/O failure on attempt {}/{}", attempt, MAX_ATTEMPTS, ex);
                    if (ex instanceof HttpTimeoutException || containsIgnoreCase(ex.getMessage(), "timeout")) {
                        throw openAiTransportException(ex);
                    }
                    if (attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw openAiTransportException(ex);
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
        return structuredRequest(name, schema, systemPrompt, userPrompt, properties.getModel());
    }

    private Map<String, Object> structuredRequest(String name, Map<String, Object> schema, String systemPrompt,
                                                  String userPrompt, String model) {
        Map<String, Object> body = baseBody(model);
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

    private Map<String, Object> textRequest(String systemPrompt, List<Map<String, Object>> content) {
        Map<String, Object> body = baseBody(properties.getModel());
        body.put("input", List.of(
                message("system", List.of(inputText(systemPrompt))),
                message("user", content)));
        return body;
    }

    /** override 가 비어 있으면 공용 모델을 쓴다(기존 동작). */
    private String resolveModel(String modelOverride) {
        return (modelOverride != null && !modelOverride.isBlank())
                ? modelOverride.trim()
                : properties.getModel();
    }

    private Map<String, Object> baseBody(String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
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

    /** OCR 전용: 빈 결과에 예외를 던지지 않고 "" 를 반환한다(재시도·상위 폴백이 처리). 코드펜스만 제거. */
    private String cleanOcrText(JsonNode root) {
        String text = outputText(root).trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
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
        return usage(root, properties.getModel());
    }

    private Usage usage(JsonNode root, String model) {
        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
        return new Usage(model, inputTokens, outputTokens, totalTokens);
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    static JobPostingMetadataPayload parseJobPostingMetadataPayload(JsonNode payload, Usage usage) {
        return new JobPostingMetadataPayload(
                payload.path("companyName").asText(""),
                payload.path("jobTitle").asText(""),
                null,
                date(payload, "deadlineDate"),
                usage);
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
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

    private String json(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return defaultValue;
        }
    }

    private JsonNode parseResponseBody(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OpenAI 응답 JSON을 해석하지 못했습니다.");
        }
    }

    private BusinessException openAiTransportException(IOException ex) {
        if (ex instanceof HttpTimeoutException || containsIgnoreCase(ex.getMessage(), "timeout")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "OpenAI 요청이 %d초 안에 완료되지 않아 중단되었습니다. 추출 결과는 저장되지 않았습니다. PDF 파일이 크거나 스캔 페이지가 많으면 더 작은 파일로 나눠 다시 시도해 주세요."
                            .formatted(properties.getTimeout().toSeconds()));
        }
        return new BusinessException(ErrorCode.INTERNAL_ERROR,
                "OpenAI API와 통신하지 못했습니다. 네트워크 상태와 API 설정을 확인해 주세요.");
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
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
        properties.put("evidence", evidenceArraySchema());
        properties.put("ambiguousConditions", ambiguousConditionsArraySchema());
        return objectSchema(properties, List.of(
                "employmentType", "experienceLevel", "requiredSkills", "preferredSkills",
                "duties", "qualifications", "difficulty", "summary", "evidence", "ambiguousConditions"));
    }

    /**
     * 기업분석 canonical contract 의 OpenAI 표현. strict=true 에서는 properties 의 모든 키가
     * required 여야 하므로, 서버 canonicalizer 가 보정하는 optional 키(factId·sourceKind·sourceRef·
     * inferenceId·basedOn·confidence·neededSource)는 required + nullable 타입으로 표현한다
     * (기존 {@link #nullableStringSchema()} 패턴). 로컬/Claude 경로
     * ({@code BAnalysisGenerationService#companyAnalysisSchema})와 필드 집합이 동일해야 하며,
     * sources 도 string[] 이 아니라 {type,label} 객체 배열로 통일한다.
     */
    Map<String, Object> companyAnalysisSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("companySummary", stringSchema());
        properties.put("recentIssues", stringSchema());
        properties.put("industry", stringSchema());
        properties.put("competitors", stringArraySchema());
        properties.put("interviewPoints", stringSchema());
        properties.put("sources", sourcesArraySchema());
        properties.put("verifiedFacts", verifiedFactsArraySchema());
        properties.put("aiInferences", aiInferencesArraySchema());
        properties.put("unknowns", unknownsArraySchema());
        return objectSchema(properties, List.of(
                "companySummary", "recentIssues", "industry", "competitors", "interviewPoints", "sources",
                "verifiedFacts", "aiInferences", "unknowns"));
    }

    private Map<String, Object> jobPostingMetadataSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("companyName", stringSchema());
        properties.put("jobTitle", stringSchema());
        properties.put("postingDate", nullableStringSchema());
        properties.put("deadlineDate", nullableStringSchema());
        return objectSchema(properties, List.of("companyName", "jobTitle", "postingDate", "deadlineDate"));
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

    private Map<String, Object> nullableStringSchema() {
        return Map.of("type", List.of("string", "null"));
    }

    /** 배열형 optional(basedOn 등)의 OpenAI strict 허용 nullable 표현. */
    private Map<String, Object> nullableStringArraySchema() {
        return Map.of("type", List.of("array", "null"), "items", Map.of("type", "string"));
    }

    private Map<String, Object> stringArraySchema() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private Map<String, Object> evidenceArraySchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("field", stringSchema());
        itemProperties.put("quote", stringSchema());
        return Map.of("type", "array", "items", objectSchema(itemProperties, List.of("field", "quote")));
    }

    private Map<String, Object> ambiguousConditionsArraySchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("condition", stringSchema());
        itemProperties.put("assumption", stringSchema());
        return Map.of("type", "array", "items", objectSchema(itemProperties, List.of("condition", "assumption")));
    }

    private Map<String, Object> sourcesArraySchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("type", stringSchema());
        itemProperties.put("label", stringSchema());
        return Map.of("type", "array", "items", objectSchema(itemProperties, List.of("type", "label")));
    }

    private Map<String, Object> verifiedFactsArraySchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("fact", stringSchema());
        itemProperties.put("source", stringSchema());
        itemProperties.put("evidence", stringSchema());
        itemProperties.put("factId", nullableStringSchema());
        itemProperties.put("sourceKind", nullableStringSchema());
        itemProperties.put("sourceRef", nullableStringSchema());
        return Map.of("type", "array", "items", objectSchema(itemProperties,
                List.of("fact", "source", "evidence", "factId", "sourceKind", "sourceRef")));
    }

    private Map<String, Object> aiInferencesArraySchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("inference", stringSchema());
        itemProperties.put("basis", stringSchema());
        itemProperties.put("inferenceId", nullableStringSchema());
        itemProperties.put("basedOn", nullableStringArraySchema());
        itemProperties.put("confidence", nullableStringSchema());
        return Map.of("type", "array", "items", objectSchema(itemProperties,
                List.of("inference", "basis", "inferenceId", "basedOn", "confidence")));
    }

    private Map<String, Object> unknownsArraySchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("topic", stringSchema());
        itemProperties.put("reason", stringSchema());
        itemProperties.put("neededSource", nullableStringSchema());
        return Map.of("type", "array", "items", objectSchema(itemProperties,
                List.of("topic", "reason", "neededSource")));
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
            String evidence,
            String ambiguousConditions,
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
            String verifiedFacts,
            String aiInferences,
            String unknowns,
            Usage usage
    ) {
    }

    public record JobPostingMetadataPayload(
            String companyName,
            String jobTitle,
            LocalDate postingDate,
            LocalDate deadlineDate,
            Usage usage
    ) {
    }
}
