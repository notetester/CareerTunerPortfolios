package com.careertuner.interview.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.careertuner.ai.common.model.AiProviderChain;
import com.careertuner.ai.common.model.AiProviderTier;
import com.careertuner.ai.common.model.RequestedAiModel;

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

    /** 생성 tier 순서. AUTO 는 이 전체(자체모델은 화이트리스트 task 만), 명시 선택은 그 tier 부터. */
    private static final List<AiProviderTier> DEFAULT_ORDER =
            List.of(AiProviderTier.CAREERTUNER, AiProviderTier.CLAUDE, AiProviderTier.OPENAI);

    private final OssLlmGateway oss;
    private final AnthropicLlmGateway anthropic;
    private final OpenAiLlmGateway openAi;
    private final MockInterviewLlmGateway mock;
    private final InterviewEvalProperties evalProperties;
    private final InterviewModelSelectionTrace selectionTrace;

    public FallbackInterviewLlmGateway(OssLlmGateway oss, AnthropicLlmGateway anthropic,
                                       OpenAiLlmGateway openAi, MockInterviewLlmGateway mock,
                                       InterviewEvalProperties evalProperties,
                                       InterviewModelSelectionTrace selectionTrace) {
        this.oss = oss;
        this.anthropic = anthropic;
        this.openAi = openAi;
        this.mock = mock;
        this.evalProperties = evalProperties;
        this.selectionTrace = selectionTrace;
    }

    /**
     * 사용자 선택 모델 tier 부터 시작하는 생성 폴백(요청 스코프 {@link InterviewModelSelectionTrace} 기준).
     * {@code AUTO}(미설정 포함)는 현행과 동일 — 자체모델은 학습된 생성 task({@code OSS_GENERATION_TASKS}, 현재 빈)만,
     * 그 외엔 Claude → OpenAI → Mock. 명시 {@code CAREERTUNER}는 화이트리스트를 우회해 서빙되면 자체모델을 시도한다
     * (QGEN 형식 불안정 위험은 사용자 선택). 어느 경우든 목업 안전망까지 폴백해 화면이 깨지지 않는다.
     */
    @Override
    public Result complete(Request request) {
        RequestedAiModel requestedModel = selectionTrace.current();
        boolean explicitSelf = requestedModel == RequestedAiModel.CAREERTUNER;
        for (AiProviderTier tier : AiProviderChain.startingFrom(requestedModel, DEFAULT_ORDER)) {
            switch (tier) {
                case CAREERTUNER -> {
                    // AUTO: 기존 화이트리스트 규칙. 명시 CAREERTUNER: 화이트리스트 무시하고 서빙되면 시도.
                    boolean ossEligible = explicitSelf
                            ? oss.available()
                            : (evalProperties.isOss() && oss.available()
                                    && OSS_GENERATION_TASKS.contains(request.schemaName()));
                    if (ossEligible) {
                        try {
                            return oss.complete(request);
                        } catch (RuntimeException ex) {
                            log.warn("자체 모델 호출 실패 → 다음 폴백 ({}): {}", request.schemaName(), ex.getMessage());
                        }
                    }
                }
                case CLAUDE -> {
                    if (anthropic.available()) {
                        try {
                            return anthropic.complete(request);
                        } catch (RuntimeException ex) {
                            log.warn("Claude 호출 실패 → 다음 폴백 ({}): {}", request.schemaName(), ex.getMessage());
                        }
                    }
                }
                case OPENAI -> {
                    if (openAi.available()) {
                        try {
                            return openAi.complete(request);
                        } catch (RuntimeException ex) {
                            log.warn("OpenAI 호출 실패 → 목업 폴백 ({}): {}", request.schemaName(), ex.getMessage());
                        }
                    }
                }
            }
        }
        // 최종 폴백: 목업 — 외부 LLM 이 모두 미설정/실패해도 화면이 깨지지 않게 한다.
        log.warn("모든 외부 LLM provider 미설정/실패 → 목업으로 응답 ({})", request.schemaName());
        return mock.complete(request);
    }
}
