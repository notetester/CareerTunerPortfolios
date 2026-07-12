package com.careertuner.interview.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.ai.prompt.InterviewPromptCatalog;

import tools.jackson.databind.JsonNode;

/**
 * 면접 도메인 구조화 LLM 호출 오케스트레이션.
 *
 * <p>프롬프트 구성·JSON 스키마·응답 매핑(도메인 로직)만 담당하고, 실제 provider 전송은
 * {@link InterviewLlmGateway}(@Primary {@link FallbackInterviewLlmGateway})에 위임한다.
 * 그래서 이 클래스의 모든 호출은 자동으로 "자체 모델(학습된 생성 task) → Claude → OpenAI" 순으로 폴백한다.
 * (클래스명은 호환을 위해 유지하지만, 전송은 더 이상 OpenAI 전용이 아니다.)
 *
 * <p>{@link InterviewAnswerEvaluator} 를 구현해 평가/Critic 경로의 기본 평가기로도 쓰인다.
 */
@Service
public class InterviewOpenAiClient implements InterviewAnswerEvaluator {

    private final InterviewModelProperties modelProperties;
    private final InterviewLlmGateway gateway;

    public InterviewOpenAiClient(InterviewModelProperties modelProperties, InterviewLlmGateway gateway) {
        this.modelProperties = modelProperties;
        this.gateway = gateway;
    }

    /** 지원 건 + 공고 기반 예상 질문 생성. */
    public GeneratedQuestions generateQuestions(ApplicationCase applicationCase, String postingText,
                                                String modeLabel, int count) {
        return generateQuestions(applicationCase, postingText, modeLabel, count, "");
    }

    /** 지원 건 + A/B/C 정본 컨텍스트 기반 예상 질문 생성. */
    public GeneratedQuestions generateQuestions(ApplicationCase applicationCase, String postingText,
                                                String modeLabel, int count, String preparationContext) {
        String context = preparationContext == null || preparationContext.isBlank()
                ? ""
                : "\n" + preparationContext.trim() + "\n";
        String userPrompt = """
                회사명: %s
                직무명: %s
                면접 모드: %s
                생성할 질문 수: %d
                %s

                채용공고:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                modeLabel, count, context,
                postingText == null || postingText.isBlank() ? "(공고문 없음)" : postingText);

        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_questions", questionsSchema(),
                InterviewPromptCatalog.QUESTION_SYSTEM_PROMPT, userPrompt, modelProperties.getGeneration()));
        JsonNode payload = result.payload();

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
        return new GeneratedQuestions(questions, result.usage());
    }

    /**
     * 단일 답변 평가(점수/피드백/개선답변). ragContext 가 있으면 평가 근거로 함께 제공한다.
     * referenceModelAnswer(사용자에게 보여준 모범답안)가 있으면 만점 기준 답안지로 함께 넘긴다.
     */
    public AnswerEvaluation evaluateAnswer(String question, String answerText, ApplicationCase applicationCase,
                                           String ragContext, String referenceModelAnswer) {
        String reference = ragContext == null || ragContext.isBlank()
                ? ""
                : "\n참고 자료(평가 기준·지식베이스):\n" + ragContext + "\n";
        String modelKey = referenceModelAnswer == null || referenceModelAnswer.isBlank()
                ? ""
                : "\n기준 모범답안(이 답안을 만점 기준으로 삼는다):\n" + referenceModelAnswer + "\n";
        String userPrompt = """
                회사명: %s
                직무명: %s
                %s%s
                질문:
                %s

                지원자 답변:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                reference, modelKey, question, answerText);

        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_answer_evaluation", evaluationSchema(),
                InterviewPromptCatalog.EVALUATION_SYSTEM_PROMPT, userPrompt, modelProperties.getJudge()));
        JsonNode payload = result.payload();
        return new AnswerEvaluation(
                clampScore(payload.path("score").asInt(0)),
                payload.path("feedback").asText(""),
                payload.path("improvedAnswer").asText(""),
                result.usage());
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

        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_model_answer", modelAnswerSchema(),
                InterviewPromptCatalog.MODEL_ANSWER_SYSTEM_PROMPT, userPrompt, modelProperties.getGeneration()));
        JsonNode payload = result.payload();
        String modelAnswer = payload.path("modelAnswer").asText("").trim();
        if (modelAnswer.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI가 모범답안을 생성하지 못했습니다.");
        }
        return new ModelAnswer(modelAnswer, result.usage());
    }

    /**
     * 여러 질문의 모범답안을 한 번의 호출로 일괄 생성한다(질문 생성 시 미리 저장용).
     * 반환 리스트는 입력 질문 순서에 맞춘다. 일부가 비면 해당 칸은 null 일 수 있다.
     */
    public GeneratedModelAnswers generateModelAnswers(List<String> questions, ApplicationCase applicationCase,
                                                      String modeLabel) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            list.append(i + 1).append(". ").append(questions.get(i)).append("\n");
        }
        String userPrompt = """
                회사명: %s
                직무명: %s
                면접 모드: %s

                아래 각 질문에 대한 모범답안을 작성하라. 반드시 질문 번호(number)를 그대로 달아 답한다.
                질문 목록:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(), modeLabel, list);

        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_model_answers", modelAnswersSchema(),
                InterviewPromptCatalog.MODEL_ANSWER_SYSTEM_PROMPT, userPrompt, modelProperties.getGeneration()));
        JsonNode payload = result.payload();

        String[] answers = new String[questions.size()];
        JsonNode array = payload.path("answers");
        if (array.isArray()) {
            for (JsonNode item : array) {
                int number = item.path("number").asInt(0);
                String answer = item.path("modelAnswer").asText("").trim();
                if (number >= 1 && number <= questions.size() && !answer.isBlank()) {
                    answers[number - 1] = answer;
                }
            }
        }
        return new GeneratedModelAnswers(Arrays.asList(answers), result.usage());
    }

    /** 원 질문 + 지원자 답변 기반 꼬리 질문 생성. */
    public GeneratedQuestions generateFollowUps(String question, String answerText,
                                                ApplicationCase applicationCase, int count, boolean pressure) {
        return generateFollowUps(question, answerText, applicationCase, count, pressure, "");
    }

    /** 원 질문 + 지원자 답변 + 질문 생성 당시 적합도 스냅샷 기반 꼬리 질문 생성. */
    public GeneratedQuestions generateFollowUps(String question, String answerText,
                                                ApplicationCase applicationCase, int count, boolean pressure,
                                                String fitContext) {
        String context = fitContext == null || fitContext.isBlank() ? "" : "\n" + fitContext.trim() + "\n";
        String userPrompt = """
                회사명: %s
                직무명: %s
                생성할 꼬리 질문 수: %d
                %s

                원 질문:
                %s

                지원자 답변:
                %s
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                count, context, question, answerText == null || answerText.isBlank() ? "(답변 없음)" : answerText);

        String systemPrompt = pressure
                ? InterviewPromptCatalog.PRESSURE_FOLLOWUP_SYSTEM_PROMPT
                : InterviewPromptCatalog.FOLLOWUP_SYSTEM_PROMPT;
        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_follow_up_questions", questionsSchema(),
                systemPrompt, userPrompt, modelProperties.getGeneration()));
        JsonNode payload = result.payload();

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
        return new GeneratedQuestions(questions, result.usage());
    }

    /** Critic 에이전트: 원 채점 결과를 적대적으로 검증하고 필요 시 점수를 조정한다. */
    public CritiqueResult critiqueEvaluation(String question, String answerText, int originalScore, String feedback,
                                             String referenceModelAnswer) {
        String modelKey = referenceModelAnswer == null || referenceModelAnswer.isBlank()
                ? ""
                : "\n기준 모범답안(이 답안을 만점 기준으로 삼는다):\n" + referenceModelAnswer + "\n";
        String userPrompt = """
                질문:
                %s

                지원자 답변:
                %s
                %s
                원 채점 점수: %d
                원 채점 피드백:
                %s
                """.formatted(question, answerText, modelKey, originalScore, feedback == null ? "" : feedback);

        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_critique", critiqueSchema(),
                InterviewPromptCatalog.CRITIC_SYSTEM_PROMPT, userPrompt, modelProperties.getJudge()));
        JsonNode payload = result.payload();
        return new CritiqueResult(
                clampScore(payload.path("adjustedScore").asInt(originalScore)),
                payload.path("verdict").asText("유지"),
                payload.path("reason").asText(""),
                result.usage());
    }

    /** 평가 하니스용: 독립 심사위원이 같은 답변을 재채점한다(LLM-as-judge). */
    public ScoreOnly judgeAnswerScore(String question, String answerText) {
        String userPrompt = """
                질문:
                %s

                지원자 답변:
                %s
                """.formatted(question, answerText);
        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_judge", scoreOnlySchema(),
                InterviewPromptCatalog.JUDGE_SYSTEM_PROMPT, userPrompt, modelProperties.getJudge()));
        JsonNode payload = result.payload();
        return new ScoreOnly(clampScore(payload.path("score").asInt(0)), result.usage());
    }

    /** 면접 전체 Q&A 기반 종합 리포트 생성. */
    public ReportPayload generateReport(String transcript) {
        return generateReport(transcript, "");
    }

    /** 질문 생성 당시 적합도 분석 스냅샷을 포함한 종합 리포트 생성. */
    public ReportPayload generateReport(String transcript, String fitContext) {
        String prompt = fitContext == null || fitContext.isBlank()
                ? transcript
                : fitContext.trim() + "\n\n" + transcript;
        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_report", reportSchema(),
                InterviewPromptCatalog.REPORT_SYSTEM_PROMPT, prompt, modelProperties.getGeneration()));
        JsonNode payload = result.payload();

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
        return new ReportPayload(clampScore(payload.path("totalScore").asInt(0)), categories, summary, result.usage());
    }

    /** LLM Planner(자율 루프 시연 모드): 현재 상태와 가용 액션을 보고 다음 액션을 하나 고른다. */
    public PlanDecisionResult planNextAction(String stateSummary, List<String> availableActions) {
        String userPrompt = """
                현재 답변 평가 상태:
                %s

                지금 고를 수 있는 액션: %s
                """.formatted(stateSummary, String.join(", ", availableActions));
        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_agent_plan", planSchema(availableActions),
                InterviewPromptCatalog.PLANNER_SYSTEM_PROMPT, userPrompt, modelProperties.getJudge()));
        JsonNode payload = result.payload();
        return new PlanDecisionResult(
                payload.path("action").asText(""),
                payload.path("reason").asText(""),
                result.usage());
    }

    /**
     * 음성 모의면접 트랜스크립트를 질문별 답변으로 매핑하고 채점한다(통합 1콜).
     * 대화 흐름(면접관/지원자 발화)에서 각 질문의 답을 추출해 점수·피드백을 매긴다. 미응답 질문은 score 0.
     */
    public VoiceScoringResult scoreVoiceTranscript(List<String> questions, List<String> modelAnswers,
                                                   String transcriptText,
                                                   String companyName, String jobTitle) {
        return scoreVoiceTranscript(questions, modelAnswers, transcriptText, companyName, jobTitle, "");
    }

    /** 질문 생성 당시 적합도 스냅샷을 포함한 음성 면접 채점. */
    public VoiceScoringResult scoreVoiceTranscript(List<String> questions, List<String> modelAnswers,
                                                   String transcriptText,
                                                   String companyName, String jobTitle, String fitContext) {
        StringBuilder qList = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            qList.append(i + 1).append(". ").append(questions.get(i)).append("\n");
            // 텍스트 면접(evaluateAnswer)과 동일하게 모범답안을 만점 기준으로 주입한다(§4.10 채점 레이어 통일).
            // 모범답안이 비어 있으면(백그라운드 미생성) 그 질문만 일반 채점으로 폴백한다.
            String model = modelAnswers != null && i < modelAnswers.size() ? modelAnswers.get(i) : null;
            if (model != null && !model.isBlank()) {
                qList.append("   [기준 모범답안] ").append(model.trim()).append("\n");
            }
        }
        String system = """
                너는 음성 모의면접 채점관이다. 면접관(ai)과 지원자(user)가 주고받은 대화에서
                각 준비 질문(number)에 대해 지원자가 실제로 말한 답을 찾아 정리하고 채점한다.
                JSON 만 반환한다. 지원자가 답하지 않은 질문은 answer 를 빈 문자열, score 를 0 으로 둔다.
                질문에 [기준 모범답안] 이 주어지면 그 답안을 만점 기준으로 삼아, 지원자 답변이 그 핵심
                내용에 얼마나 부합하는지로 0~100 채점한다. 모범답안이 없는 질문만 내용의 구체성·직무
                적합성·논리성을 기준으로 0~100 채점한다. 채점 후 한국어 한두 문장으로 피드백한다.
                """;
        String userPrompt = """
                회사명: %s
                직무명: %s
                %s

                준비된 질문(number 로 참조):
                %s
                대화 트랜스크립트(role: ai=면접관, user=지원자):
                %s
                """.formatted(companyName, jobTitle,
                fitContext == null || fitContext.isBlank() ? "" : fitContext.trim(), qList, transcriptText);

        InterviewLlmGateway.Result result = gateway.complete(new InterviewLlmGateway.Request(
                "interview_voice_transcript_scoring", voiceScoringSchema(),
                system, userPrompt, modelProperties.getJudge()));
        JsonNode payload = result.payload();

        List<VoiceScoredItem> items = new ArrayList<>();
        JsonNode arr = payload.path("results");
        if (arr.isArray()) {
            for (JsonNode it : arr) {
                items.add(new VoiceScoredItem(
                        it.path("number").asInt(0),
                        it.path("answer").asText("").trim(),
                        clampScore(it.path("score").asInt(0)),
                        it.path("feedback").asText("").trim()));
            }
        }
        return new VoiceScoringResult(items, result.usage());
    }

    // ───── 응답 매핑 보조 ─────

    private String normalizeType(String value) {
        // 본질문 생성 전용. FOLLOW_UP 은 꼬리질문(generateFollowUps)에서만 직접 부여하므로 여기서는 제외한다.
        // (LLM 이 본질문을 FOLLOW_UP 으로 오분류하면 꼬리질문처럼 표시되는 버그 방지)
        return switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "TECH", "PERSONALITY", "SITUATION", "EXPECTED" -> value.toUpperCase(Locale.ROOT);
            default -> "EXPECTED";
        };
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ───── JSON Schema ─────

    private Map<String, Object> questionsSchema() {
        // 본질문/꼬리질문이 공유하는 스키마. 본질문은 이 type 을 그대로 저장하므로 FOLLOW_UP 을 enum 에서 뺀다.
        // 꼬리질문(generateFollowUps)은 응답 type 을 무시하고 항상 FOLLOW_UP 으로 강제하므로 영향 없다.
        Map<String, Object> questionItem = objectSchema(
                Map.of(
                        "question", stringSchema(),
                        "type", Map.of("type", "string",
                                "enum", List.of("EXPECTED", "TECH", "PERSONALITY", "SITUATION"))),
                List.of("question", "type"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("questions", Map.of("type", "array", "items", questionItem));
        return objectSchema(properties, List.of("questions"));
    }

    private Map<String, Object> evaluationSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("score", integerSchema());
        properties.put("feedback", stringSchema());
        return objectSchema(properties, List.of("score", "feedback"));
    }

    private Map<String, Object> modelAnswerSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("modelAnswer", stringSchema());
        return objectSchema(properties, List.of("modelAnswer"));
    }

    private Map<String, Object> modelAnswersSchema() {
        Map<String, Object> answerItem = objectSchema(
                Map.of("number", integerSchema(), "modelAnswer", stringSchema()),
                List.of("number", "modelAnswer"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("answers", Map.of("type", "array", "items", answerItem));
        return objectSchema(properties, List.of("answers"));
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

    private Map<String, Object> voiceScoringSchema() {
        Map<String, Object> item = objectSchema(
                Map.of(
                        "number", integerSchema(),
                        "answer", stringSchema(),
                        "score", integerSchema(),
                        "feedback", stringSchema()),
                List.of("number", "answer", "score", "feedback"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("results", Map.of("type", "array", "items", item));
        return objectSchema(properties, List.of("results"));
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

    /** 여러 질문의 모범답안(입력 순서에 정렬, 빈 칸은 null 가능). */
    public record GeneratedModelAnswers(List<String> modelAnswers, Usage usage) {
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

    /** 음성 트랜스크립트 채점 결과의 한 항목 (number 는 1-base 질문 순번). */
    public record VoiceScoredItem(int number, String answerText, int score, String feedback) {
    }

    public record VoiceScoringResult(List<VoiceScoredItem> items, Usage usage) {
    }
}
