package com.careertuner.analysis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;

/**
 * 커리어 트렌드 AI 진입점(@Primary) — Claude(Haiku) → OpenAI → Mock 폴백 디스패처.
 *
 * <p>Anthropic 키가 있으면 Claude(공통 키라 가장 안정적인 1차 폴백)를 먼저 시도하고, 실패하면
 * {@link OpenAiCareerTrendAiService}(키 없거나 실패 시 내부 Mock 으로 폴백 — 최종 안전망)로 넘어간다.
 * 따라서 외부 LLM 이 모두 죽어도 화면은 깨지지 않는다. 키가 비어 있으면 Claude 단계는 건너뛴다.
 *
 * <p><b>체인 총 시간예산</b>({@code careertuner.analysis.ai.chain-total-time-budget}, 기본 120s): Claude 가
 * 예산을 소진하면 OpenAI 를 건너뛰고 즉시 {@link MockCareerTrendAiService} 안전망을 반환한다
 * (per-timeout 합 대신 유계 응답). 예산 0/음수면 무제한(끔).
 */
@Primary
@Service
public class FallbackCareerTrendAiService implements CareerTrendAiService {

    private static final Logger log = LoggerFactory.getLogger(FallbackCareerTrendAiService.class);

    private final AnthropicCareerTrendAiService anthropicService;
    private final OpenAiCareerTrendAiService openAiService;
    private final MockCareerTrendAiService mockService;
    private final CareerAnalysisAiProviderProperties properties;

    public FallbackCareerTrendAiService(AnthropicCareerTrendAiService anthropicService,
                                        OpenAiCareerTrendAiService openAiService,
                                        MockCareerTrendAiService mockService,
                                        CareerAnalysisAiProviderProperties properties) {
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.mockService = mockService;
        this.properties = properties;
    }

    @Override
    public CareerTrendAiResult generate(CareerTrendAiCommand command) {
        AiTotalTimeBudget chain = AiTotalTimeBudget.start(properties.getChainTotalTimeBudget());
        if (!chain.expired() && anthropicService.configured()) {
            try {
                return anthropicService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 커리어트렌드 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        if (!chain.expired()) {
            return openAiService.generate(command);
        }
        log.warn("C 커리어트렌드 체인 시간예산 {} 소진 → Mock 안전망 즉시 반환", properties.getChainTotalTimeBudget());
        return mockService.generate(command);
    }
}
