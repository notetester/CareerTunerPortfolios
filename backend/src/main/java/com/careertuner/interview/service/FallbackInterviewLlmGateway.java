package com.careertuner.interview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 면접 LLM 디스패처: Claude(Haiku) 우선 → 실패/한도초과/미설정 시 OpenAI 폴백.
 *
 * <p>호출부({@link InterviewOpenAiClient})는 이 @Primary 게이트웨이만 주입받으므로,
 * provider 전략은 여기 한 곳에서만 결정된다. 선생(자체 LLM 학습 데이터)·런타임을 Claude 로 통일한다.
 * <ul>
 *   <li>Anthropic 키 있음 → Claude 시도, BusinessException(요청 실패·한도초과 등) 발생 시 OpenAI 로 폴백</li>
 *   <li>Anthropic 키 없음 → 곧장 OpenAI</li>
 *   <li>둘 다 없음 → 명확한 예외</li>
 * </ul>
 */
@Primary
@Component
public class FallbackInterviewLlmGateway implements InterviewLlmGateway {

    private static final Logger log = LoggerFactory.getLogger(FallbackInterviewLlmGateway.class);

    private final AnthropicLlmGateway anthropic;
    private final OpenAiLlmGateway openAi;

    public FallbackInterviewLlmGateway(AnthropicLlmGateway anthropic, OpenAiLlmGateway openAi) {
        this.anthropic = anthropic;
        this.openAi = openAi;
    }

    @Override
    public Result complete(Request request) {
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
