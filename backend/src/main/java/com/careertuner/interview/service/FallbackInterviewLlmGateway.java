package com.careertuner.interview.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 면접 LLM 디스패처: 자체 모델(학습된 생성 task) → Claude(Haiku) → OpenAI 순으로 폴백.
 *
 * <p>호출부({@link InterviewOpenAiClient})는 이 @Primary 게이트웨이만 주입받으므로,
 * provider 전략은 여기 한 곳에서만 결정된다.
 * <ul>
 *   <li>provider=oss + 자체 모델 서빙됨 + 학습된 생성 task → 자체 모델(OSS) 1차, 실패 시 아래로 폴백</li>
 *   <li>Anthropic 키 있음 → Claude 시도, BusinessException(요청 실패·한도초과 등) 발생 시 OpenAI 로 폴백</li>
 *   <li>Anthropic 키 없음 → 곧장 OpenAI</li>
 *   <li>둘 다 없음 → 명확한 예외</li>
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
     * 자체 모델이 학습해 1차로 보낼 수 있는 생성 task 의 schemaName 집합(현재: 질문생성·모범답안).
     * 미학습 task(꼬리질문·리포트·planner·judge)는 여기 없으므로 Claude 가 담당한다.
     * 학습이 확대되면 이 집합을 넓혀 단계적으로 Claude 를 폐기한다.
     */
    private static final Set<String> OSS_GENERATION_TASKS = Set.of(
            "interview_questions", "interview_model_answer", "interview_model_answers");

    private final OssLlmGateway oss;
    private final AnthropicLlmGateway anthropic;
    private final OpenAiLlmGateway openAi;
    private final InterviewEvalProperties evalProperties;

    public FallbackInterviewLlmGateway(OssLlmGateway oss, AnthropicLlmGateway anthropic,
                                       OpenAiLlmGateway openAi, InterviewEvalProperties evalProperties) {
        this.oss = oss;
        this.anthropic = anthropic;
        this.openAi = openAi;
        this.evalProperties = evalProperties;
    }

    @Override
    public Result complete(Request request) {
        // 1) 자체 모델 우선 — provider=oss + 서빙됨 + 학습된 생성 task 일 때만. 실패 시 Claude/OpenAI 로 폴백.
        if (evalProperties.isOss() && oss.available() && OSS_GENERATION_TASKS.contains(request.schemaName())) {
            try {
                return oss.complete(request);
            } catch (BusinessException ex) {
                log.warn("자체 모델 호출 실패 → Claude/OpenAI 폴백 ({}): {}", request.schemaName(), ex.getMessage());
            }
        }
        // 2) Claude(Haiku) 우선 → 실패 시 OpenAI.
        if (anthropic.available()) {
            try {
                return anthropic.complete(request);
            } catch (BusinessException ex) {
                if (openAi.available()) {
                    log.warn("Claude 호출 실패 → OpenAI 폴백 ({}): {}", request.schemaName(), ex.getMessage());
                    return openAi.complete(request);
                }
                throw ex;
            }
        }
        if (openAi.available()) {
            return openAi.complete(request);
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "LLM provider 가 설정되어 있지 않습니다. ANTHROPIC_API_KEY 또는 OPENAI_API_KEY 를 설정하세요.");
    }
}
