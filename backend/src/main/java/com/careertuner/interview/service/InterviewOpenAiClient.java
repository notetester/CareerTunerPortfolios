package com.careertuner.interview.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.ai.prompt.InterviewPromptCatalog;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 면접 도메인 전용 OpenAI Responses API 클라이언트.
 * 공유 설정(OpenAiProperties)은 재사용하되, 호출/파싱은 면접 기능에 맞게 자체 구현한다.
 */
@Service
public class InterviewOpenAiClient implements InterviewAnswerEvaluator {

    private static final int MAX_ATTEMPTS = 3;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InterviewOpenAiClient(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    /** 지원 건 + 공고 기반 예상 질문 생성. */
    public GeneratedQuestions generateQuestions(ApplicationCase applicationCase, String postingText,
                                                String modeLabel, int count) {
        String userPrompt = """
                회사명: %s
                직무명: %s
                면접 모드: %s
                생성할 질문 수: %d

                채용공고:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                modeLabel, count, postingText == null || postingText.isBlank() ? "(공고문 없음)" : postingText);

        JsonNode root = post(structuredRequest("interview_questions", questionsSchema(),
                InterviewPromptCatalog.QUESTION_SYSTEM_PROMPT, userPrompt));
        JsonNode payload = parseOutputJson(root);

        List<GeneratedQuestion> questions = new ArrayList<>();
        JsonNode array = payload.path("questions");
        if (array.isArray()) {
            for (JsonNode item : array) {
                String q = item.path("question").asText("").trim();
                if (q.isBlank()) {
                    continue;
                }
                questions.add(new GeneratedQuestion(q, normalizeType(item.path("type").asText(""))));
            }
        }
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI가 질문을 생성하지 못했습니다.");
        }
        return new GeneratedQuestions(questions, usage(root));
    }

    /** 단일 답변 평가(점수/피드백/개선답변). ragContext 가 있으면 평가 근거로 함께 제공한다. */
    public AnswerEvaluation evaluateAnswer(String question, String answerText, ApplicationCase applicationCase,
                                           String ragContext) {
        String reference = ragContext == null || ragContext.isBlank()
                ? ""
                : "\n참고 자료(평가 기준·지식베이스):\n" + ragContext + "\n";
        String userPrompt = """
                회사명: %s
                직무명: %s
                %s
                질문:
                %s

                지원자 답변:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                reference, question, answerText);

        JsonNode root = post(structuredRequest("interview_answer_evaluation", evaluationSchema(),
                InterviewPromptCatalog.EVALUATION_SYSTEM_PROMPT, userPrompt));
        JsonNode payload = parseOutputJson(root);
        return new AnswerEvaluation(
                clampScore(payload.path("score").asInt(0)),
                payload.path("feedback").asText(""),
                payload.path("improvedAnswer").asText(""),
                usage(root));
    }

    /** 질문에 대한 모범 답변 생성(학습용). 답변 제출 없이도 호출 가능. */
    public ModelAnswer generateModelAnswer(String question, ApplicationCase applicationCase, String modeLabel) {
        String userPrompt = """
                회사명: %s
                직무명: %s
                면접 모드: %s

                질문:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(), modeLabel, question);

        JsonNode root = post(structuredRequest("interview_model_answer", modelAnswerSchema(),
                InterviewPromptCatalog.MODEL_ANSWER_SYSTEM_PROMPT, userPrompt));
        JsonNode payload = parseOutputJson(root);
        String modelAnswer = payload.path("modelAnswer").asText("").trim();
        if (modelAnswer.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI가 모범답안을 생성하지 못했습니다.");
        }
        return new ModelAnswer(modelAnswer, usage(root));
    }

    /** 원 질문 + 지원자 답변 기반 꼬리 질문 생성. */
    public GeneratedQuestions generateFollowUps(String question, String answerText,
                                                ApplicationCase applicationCase, int count) {
        String userPrompt = """
                회사명: %s
                직무명: %s
                생성할 꼬리 질문 수: %d

                원 질문:
                %s

                지원자 답변:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                count, question, answerText == null || answerText.isBlank() ? "(답변 없음)" : answerText);

        JsonNode root = post(structuredRequest("interview_follow_up_questions", questionsSchema(),
                InterviewPromptCatalog.FOLLOWUP_SYSTEM_PROMPT, userPrompt));
        JsonNode payload = parseOutputJson(root);

        List<GeneratedQuestion> questions = new ArrayList<>();
        JsonNode array = payload.path("questions");
        if (array.isArray()) {
            for (JsonNode item : array) {
                String q = item.path("question").asText("").trim();
                if (q.isBlank()) {
                    continue;
                }
                // 꼬리 질문은 항상 FOLLOW_UP 으로 강제한다.
                questions.add(new GeneratedQuestion(q, "FOLLOW_UP"));
            }
        }
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI가 꼬리 질문을 생성하지 못했습니다.");
        }
        return new GeneratedQuestions(questions, usage(root));
    }

    /** Critic 에이전트: 원 채점 결과를 적대적으로 검증하고 필요 시 점수를 조정한다. */
    public CritiqueResult critiqueEvaluation(String question, String answerText, int originalScore, String feedback) {
        String userPrompt = """
                질문:
                %s

                지원자 답변:
                %s

                원 채점 점수: %d
                원 채점 피드백:
                %s
                """.formatted(question, answerText, originalScore, feedback == null ? "" : feedback);

        JsonNode root = post(structuredRequest("interview_critique", critiqueSchema(),
                InterviewPromptCatalog.CRITIC_SYSTEM_PROMPT, userPrompt));
        JsonNode payload = parseOutputJson(root);
        return new CritiqueResult(
                clampScore(payload.path("adjustedScore").asInt(originalScore)),
                payload.path("verdict").asText("유지"),
                payload.path("reason").asText(""),
                usage(root));
    }

    /** 평가 하니스용: 독립 심사위원이 같은 답변을 재채점한다(LLM-as-judge). */
    public ScoreOnly judgeAnswerScore(String question, String answerText) {
        String userPrompt = """
                질문:
                %s

                지원자 답변:
                %s
                """.formatted(question, answerText);
        JsonNode root = post(structuredRequest("interview_judge", scoreOnlySchema(),
                InterviewPromptCatalog.JUDGE_SYSTEM_PROMPT, userPrompt));
        JsonNode payload = parseOutputJson(root);
        return new ScoreOnly(clampScore(payload.path("score").asInt(0)), usage(root));
    }

    /** 면접 전체 Q&A 기반 종합 리포트 생성. */
    public ReportPayload generateReport(String transcript) {
        JsonNode root = post(structuredRequest("interview_report", reportSchema(),
                InterviewPromptCatalog.REPORT_SYSTEM_PROMPT, transcript));
        JsonNode payload = parseOutputJson(root);

        List<ReportCategory> categories = new ArrayList<>();
        JsonNode array = payload.path("categories");
        if (array.isArray()) {
            for (JsonNode item : array) {
                categories.add(new ReportCategory(
                        item.path("label").asText(""),
                        clampScore(item.path("score").asInt(0))));
            }
        }
        List<String> summary = new ArrayList<>();
        JsonNode lines = payload.path("summaryFeedback");
        if (lines.isArray()) {
            for (JsonNode line : lines) {
                String text = line.asText("").trim();
                if (!text.isBlank()) {
                    summary.add(text);
                }
            }
        }
        return new ReportPayload(clampScore(payload.path("totalScore").asInt(0)), categories, summary, usage(root));
    }

    /** LLM Planner(자율 루프 시연 모드): 현재 상태와 가용 액션을 보고 다음 액션을 하나 고른다. */
    public PlanDecisionResult planNextAction(String stateSummary, List<String> availableActions) {
        String userPrompt = """
                현재 답변 평가 상태:
                %s

                지금 고를 수 있는 액션: %s
                """.formatted(stateSummary, String.join(", ", availableActions));
        JsonNode root = post(structuredRequest("interview_agent_plan", planSchema(availableActions),
                InterviewPromptCatalog.PLANNER_SYSTEM_PROMPT, userPrompt));
        JsonNode payload = parseOutputJson(root);
        return new PlanDecisionResult(
                payload.path("action").asText(""),
                payload.path("reason").asText(""),
                usage(root));
    }

    // ───── HTTP / 파싱 인프라 ─────

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

    private Map<String, Object> structuredRequest(String name, Map<String, Object> schema,
                                                  String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", List.of(
                message("system", systemPrompt),
                message("user", userPrompt)));
        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", name,
                        "strict", true,
                        "schema", schema)));
        return body;
    }

    private Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "content", List.of(Map.of("type", "input_text", "text", text)));
    }

    private JsonNode parseOutputJson(JsonNode root) {
        String text = outputText(root).trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 응답 본문이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 응답이 JSON 형식이 아닙니다.");
        }
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

    private String normalizeType(String value) {
        return switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "TECH", "PERSONALITY", "SITUATION", "FOLLOW_UP", "EXPECTED" -> value.toUpperCase(Locale.ROOT);
            default -> "EXPECTED";
        };
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ───── JSON Schema ─────

    private Map<String, Object> questionsSchema() {
        Map<String, Object> questionItem = objectSchema(
                Map.of(
                        "question", stringSchema(),
                        "type", Map.of("type", "string",
                                "enum", List.of("EXPECTED", "TECH", "PERSONALITY", "SITUATION", "FOLLOW_UP"))),
                List.of("question", "type"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("questions", Map.of("type", "array", "items", questionItem));
        return objectSchema(properties, List.of("questions"));
    }

    private Map<String, Object> evaluationSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("score", integerSchema());
        properties.put("feedback", stringSchema());
        properties.put("improvedAnswer", stringSchema());
        return objectSchema(properties, List.of("score", "feedback", "improvedAnswer"));
    }

    private Map<String, Object> modelAnswerSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("modelAnswer", stringSchema());
        return objectSchema(properties, List.of("modelAnswer"));
    }

    private Map<String, Object> scoreOnlySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("score", integerSchema());
        return objectSchema(properties, List.of("score"));
    }

    private Map<String, Object> critiqueSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("adjustedScore", integerSchema());
        properties.put("verdict", Map.of("type", "string", "enum", List.of("유지", "조정")));
        properties.put("reason", stringSchema());
        return objectSchema(properties, List.of("adjustedScore", "verdict", "reason"));
    }

    private Map<String, Object> reportSchema() {
        Map<String, Object> categoryItem = objectSchema(
                Map.of("label", stringSchema(), "score", integerSchema()),
                List.of("label", "score"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("totalScore", integerSchema());
        properties.put("categories", Map.of("type", "array", "items", categoryItem));
        properties.put("summaryFeedback", Map.of("type", "array", "items", Map.of("type", "string")));
        return objectSchema(properties, List.of("totalScore", "categories", "summaryFeedback"));
    }

    private Map<String, Object> planSchema(List<String> availableActions) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of("type", "string", "enum", availableActions));
        properties.put("reason", stringSchema());
        return objectSchema(properties, List.of("action", "reason"));
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

    private Map<String, Object> integerSchema() {
        return Map.of("type", "integer");
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
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("timeout") || lower.contains("upstream connect") || lower.contains("disconnect/reset");
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

    // ───── 반환 레코드 ─────

    public record Usage(String model, int inputTokens, int outputTokens, int totalTokens) {
    }

    /** LLM Planner 의 다음 액션 결정. */
    public record PlanDecisionResult(String action, String reason, Usage usage) {
    }

    public record GeneratedQuestion(String question, String type) {
    }

    public record GeneratedQuestions(List<GeneratedQuestion> questions, Usage usage) {
    }

    public record AnswerEvaluation(int score, String feedback, String improvedAnswer, Usage usage) {
    }

    public record ModelAnswer(String modelAnswer, Usage usage) {
    }

    public record CritiqueResult(int adjustedScore, String verdict, String reason, Usage usage) {
    }

    public record ScoreOnly(int score, Usage usage) {
    }

    public record ReportCategory(String label, int score) {
    }

    public record ReportPayload(int totalScore, List<ReportCategory> categories,
                                List<String> summaryFeedback, Usage usage) {
    }
}
