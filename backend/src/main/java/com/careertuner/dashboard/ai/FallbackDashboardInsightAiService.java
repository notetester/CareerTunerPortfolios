package com.careertuner.dashboard.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 대시보드 요약 AI 진입점(@Primary) — Claude(Haiku) → OpenAI → Mock 폴백 디스패처.
 *
 * <p>Anthropic 키가 있으면 Claude 를 먼저 시도하고, 실패하면 {@link OpenAiDashboardInsightAiService}
 * (키 없거나 실패 시 내부 Mock 으로 폴백 — 최종 안전망)로 넘어간다. 키가 비어 있으면 Claude 단계는 건너뛴다.
 */
@Primary
@Service
public class FallbackDashboardInsightAiService implements DashboardInsightAiService {

    private static final Logger log = LoggerFactory.getLogger(FallbackDashboardInsightAiService.class);

    private final AnthropicDashboardInsightAiService anthropicService;
    private final OpenAiDashboardInsightAiService openAiService;

    public FallbackDashboardInsightAiService(AnthropicDashboardInsightAiService anthropicService,
                                             OpenAiDashboardInsightAiService openAiService) {
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
    }

    @Override
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command) {
        if (anthropicService.configured()) {
            try {
                return anthropicService.summarize(command);
            } catch (RuntimeException ex) {
                log.warn("C 대시보드 요약 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        return openAiService.summarize(command);
    }
}
