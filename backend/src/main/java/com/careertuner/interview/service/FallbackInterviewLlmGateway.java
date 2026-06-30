package com.careertuner.interview.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 면접 LLM 디스패처: 자체 모델(학습된 생성 task) → Claude(Haiku) → OpenAI → 목업 순으로 폴백.
 *
 * <p>호출부({@link InterviewOpenAiClient})는 이 @Primary 게이트웨이만 주입받으므로,
 * provider 전략은 여기 한 곳에서만 결정된다.
 * <ul>
 *   <li>provider=oss + 자체 모델 서빙됨 + 학습된 생성 task → 자체 모델(OSS) 1차, 실패 시 아래로 폴백</li>
 *   <li>1차 폴백 Claude(Haiku) → 실패 시 OpenAI. (Haiku 는 공통 키라 가장 안정적이라 OpenAI 앞에 둔다)</li>
 *   <li>2차 폴백 OpenAI → 실패 시 목업</li>
 *   <li>3차 폴백 목업({@link MockInterviewLlmGateway}) → 외부 provider 가 모두 미설정/실패해도
 *       화면이 깨지지 않게 형식상 유효한 응답을 반환한다(디스패처는 절대 예외로 끝나지 않는다)</li>
 * </ul>
 *
 * <p>Claude 는 자체 모델로 갈아끼우기 위한 디딤돌(선생 + 과도기 런타임)이다. 자체 모델이 학습한 task 부터
 * {@code OSS_GENERATION_TASKS} 로 점진 교체하고, 전 task 가 자체 모델로 덮이면 Claude 게이트웨이를 폐기한다.
 * 채점(EVAL)·Critic 은 이 게이트웨이가 아니라 {@link InterviewEvaluatorProvider} 가 {@link OssAnswerEvaluator}
 * 로 분기하므로 아래 화이트리스트에 넣지 않는다(이중 경로 방지).
 */
@Primary
@Component
public class FallbackInterviewLlmGateway implements InterviewLlmGateway {

    private static final Logger log = LoggerFactory.getLogger(FallbackInterviewLlmGateway.class);

    /**
     * 자체 모델이 1차로 보낼 생성 task 의 schemaName 집합.
     * ⚠️ 2026-06-20 외부 호출 검증 결과: QGEN(질문생성)은 학습 데이터가 seed당 1개로 적어 형식이 불안정하다
     * (질문 대신 프로필/환각을 뱉음). 반면 채점(EVAL)은 데이터가 많아 안정적이며, 그건 이 게이트웨이가 아니라
     * {@link InterviewEvaluatorProvider} 가 {@link OssAnswerEvaluator} 로 따로 처리한다.
     * → 현재 생성은 전부 폴백(Claude/OpenAI)으로 두고 이 집합을 비워 둔다.
     * QGEN/MODEL_ANSWER 데이터 보강(seed당 여러 개) + 재학습 후 "interview_questions",
     * "interview_model_answer", "interview_model_answers" 를 채워 단계적으로 Claude 를 폐기한다.
     */
    private static final Set<String> OSS_GENERATION_TASKS = Set.of();

    private final OssLlmGateway oss;
    private final AnthropicLlmGateway anthropic;
    private final OpenAiLlmGateway openAi;
    private final MockInterviewLlmGateway mock;
    private final InterviewEvalProperties evalProperties;

    public FallbackInterviewLlmGateway(OssLlmGateway oss, AnthropicLlmGateway anthropic,
                                       OpenAiLlmGateway openAi, MockInterviewLlmGateway mock,
                                       InterviewEvalProperties evalProperties) {
        this.oss = oss;
        this.anthropic = anthropic;
        this.openAi = openAi;
        this.mock = mock;
        this.evalProperties = evalProperties;
    }

    @Override
    public Result complete(Request request) {
        // 1) 자체 모델 우선 — provider=oss + 서빙됨 + 학습된 생성 task 일 때만. 실패 시 아래로 폴백.
        if (evalProperties.isOss() && oss.available() && OSS_GENERATION_TASKS.contains(request.schemaName())) {
            try {
                return oss.complete(request);
            } catch (RuntimeException ex) {
                log.warn("자체 모델 호출 실패 → Claude 폴백 ({}): {}", request.schemaName(), ex.getMessage());
            }
        }
        // 2) 1차 폴백: Claude(Haiku) — run-local.bat 으로 주입되는 공통 키라 가장 안정적. 실패 시 OpenAI 로.
        if (anthropic.available()) {
            try {
                return anthropic.complete(request);
            } catch (RuntimeException ex) {
                log.warn("Claude 호출 실패 → OpenAI 폴백 ({}): {}", request.schemaName(), ex.getMessage());
            }
        }
        // 3) 2차 폴백: OpenAI. 실패 시 목업으로.
        if (openAi.available()) {
            try {
                return openAi.complete(request);
            } catch (RuntimeException ex) {
                log.warn("OpenAI 호출 실패 → 목업 폴백 ({}): {}", request.schemaName(), ex.getMessage());
            }
        }
        // 4) 3차(최종) 폴백: 목업 — 외부 LLM 이 모두 미설정/실패해도 화면이 깨지지 않게 한다.
        log.warn("모든 외부 LLM provider 미설정/실패 → 목업으로 응답 ({})", request.schemaName());
        return mock.complete(request);
    }
}
