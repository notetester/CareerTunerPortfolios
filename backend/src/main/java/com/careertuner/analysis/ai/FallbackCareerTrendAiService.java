package com.careertuner.analysis.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 커리어 트렌드 AI 진입점(@Primary) — Claude(Haiku) → OpenAI → Mock 폴백 디스패처.
 *
 * <p>Anthropic 키가 있으면 Claude(공통 키라 가장 안정적인 1차 폴백)를 먼저 시도하고, 실패하면
 * {@link OpenAiCareerTrendAiService}(키 없거나 실패 시 내부 Mock 으로 폴백 — 최종 안전망)로 넘어간다.
 * 따라서 외부 LLM 이 모두 죽어도 화면은 깨지지 않는다. 키가 비어 있으면 Claude 단계는 건너뛴다.
 */
@Primary
@Service
public class FallbackCareerTrendAiService implements CareerTrendAiService {

    private static final Logger log = LoggerFactory.getLogger(FallbackCareerTrendAiService.class);

    private final AnthropicCareerTrendAiService anthropicService;
    private final OpenAiCareerTrendAiService openAiService;

    public FallbackCareerTrendAiService(AnthropicCareerTrendAiService anthropicService,
                                        OpenAiCareerTrendAiService openAiService) {
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
    }

    @Override
    public CareerTrendAiResult generate(CareerTrendAiCommand command) {
        if (anthropicService.configured()) {
            try {
                return anthropicService.generate(command);
            } catch (RuntimeException ex) {
                log.warn("C 커리어트렌드 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        return openAiService.generate(command);
    }
}
