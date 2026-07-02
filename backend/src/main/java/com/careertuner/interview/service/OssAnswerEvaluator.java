package com.careertuner.interview.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.ai.common.gpu.GpuPermitGate;
import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.interview.ai.prompt.InterviewPromptCatalog;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 자체 파인튜닝 모델(vLLM/TGI) 기반 답변 평가기.
 *
 * <p>OpenAI 호환 {@code /v1/chat/completions} 엔드포인트로 호출한다. GPU 임대로 학습한 모델
 * (예: Qwen2.5 한국어 + LoRA)을 vLLM 으로 서빙하고 {@code careertuner.interview.eval.base-url} 로 연결한다.
 *
 * <p>아직 자체 모델이 서빙되지 않은 환경(base-url 미설정)에서는 호출 시 명확한 예외를 던지고,
 * 상위({@link InterviewEvaluatorProvider}/오케스트레이터)가 OpenAI 로 폴백하도록 한다.
 * 즉 이 클래스는 "자체 모델을 직접 학습해 붙이는" 연결점이며, 실제 서빙 전까지는 폴백된다(로드맵 5장).
 */
@Component
public class OssAnswerEvaluator implements InterviewAnswerEvaluator {

    private final InterviewEvalProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GpuPermitGate gpuPermitGate;

    public OssAnswerEvaluator(InterviewEvalProperties properties, ObjectMapper objectMapper,
                              GpuPermitGate gpuPermitGate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.gpuPermitGate = gpuPermitGate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    @Override
    public InterviewOpenAiClient.AnswerEvaluation evaluateAnswer(String question, String answerText,
                                                                 ApplicationCase applicationCase, String ragContext,
                                                                 String referenceModelAnswer) {
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

                반드시 {"score": 0~100 정수, "feedback": "...", "improvedAnswer": "..."} JSON 으로만 답하라.
                """.formatted(applicationCase.getCompanyName(), applicationCase.getJobTitle(),
                reference, modelKey, question, answerText);

        JsonNode payload = chat(InterviewPromptCatalog.EVALUATION_SYSTEM_PROMPT, userPrompt);
        return new InterviewOpenAiClient.AnswerEvaluation(
                clampScore(payload.path("score").asInt(0)),
                payload.path("feedback").asText(""),
                payload.path("improvedAnswer").asText(""),
                usage());
    }

    @Override
    public InterviewOpenAiClient.CritiqueResult critiqueEvaluation(String question, String answerText,
                                                                   int originalScore, String feedback,
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

                반드시 {"adjustedScore": 0~100 정수, "verdict": "유지" 또는 "조정", "reason": "..."} JSON 으로만 답하라.
                """.formatted(question, answerText, modelKey, originalScore, feedback == null ? "" : feedback);

        JsonNode payload = chat(InterviewPromptCatalog.CRITIC_SYSTEM_PROMPT, userPrompt);
        return new InterviewOpenAiClient.CritiqueResult(
                clampScore(payload.path("adjustedScore").asInt(originalScore)),
                payload.path("verdict").asText("유지"),
                payload.path("reason").asText(""),
                usage());
    }

    // ───── vLLM/TGI (OpenAI 호환 chat completions) ─────

    private JsonNode chat(String systemPrompt, String userPrompt) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "자체 평가 모델(OSS)이 설정되어 있지 않습니다. eval.base-url 을 지정하거나 provider=openai 로 폴백하세요.");
        }
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"));
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(chatUrl()))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
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
                        "자체 평가 모델 요청 실패 (" + response.statusCode() + ")");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parseJson(content);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 평가 모델 응답을 처리하지 못했습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 평가 모델 호출이 중단되었습니다.");
        }
    }

    private JsonNode parseJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        // 소형 모델이 JSON 앞뒤에 붙이는 잡설 제거(OssLlmGateway 와 공용 로직)
        text = OssLlmGateway.extractJsonSpan(text);
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 평가 모델 응답이 비어 있습니다.");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "자체 평가 모델 응답이 JSON 형식이 아닙니다.");
        }
    }

    private String chatUrl() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        return base + "/chat/completions";
    }

    private InterviewOpenAiClient.Usage usage() {
        // 자체 서버는 과금 토큰 집계 대상이 아니므로 0 으로 기록한다(모델 id 만 남긴다).
        return new InterviewOpenAiClient.Usage(properties.getModel(), 0, 0, 0);
    }

    private int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
