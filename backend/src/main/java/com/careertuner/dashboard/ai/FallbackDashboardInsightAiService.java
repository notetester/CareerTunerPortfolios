package com.careertuner.dashboard.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.ai.common.budget.AiTotalTimeBudget;
import com.careertuner.ai.common.settings.AiRuntimeSettings;

/**
 * 대시보드 요약 AI 진입점(@Primary) — Claude(Haiku) → OpenAI 폴백 디스패처.
 *
 * <p>Anthropic 키가 있으면 Claude 를 먼저 시도하고, 실패(RuntimeException)하면 {@link OpenAiDashboardInsightAiService}
 * 로 넘어간다. 키가 비어 있으면 Claude 단계는 건너뛴다. OpenAI 단계는 항상 시도되며, 그 내부 Mock 폴백
 * (키 없음/실패 시 {@link MockDashboardInsightAiService} 결과 · FALLBACK)이 최후의 안전망이라 화면은 깨지지 않는다.
 *
 * <p><b>하이브리드 시간 정책</b>: 설정된 각 tier 는 최소 한 번은 반드시 시도되고, 그 첫 시도는 tier 별 per-attempt
 * 타임아웃({@code careertuner.analysis.ai.claude-timeout}/{@code openai-timeout}, 기본 30s)을 보장받는다. 체인 총
 * 시간예산({@code careertuner.analysis.ai.chain-total-time-budget}, 기본 120s)은 tier 를 건너뛰지 않고 각 클라이언트
 * 내부 재시도 증폭만 억제한다(예산 0/음수 = 무제한). Mock 은 별도 tier 가 아니라 OpenAI tier 내부 폴백으로만(최후) 쓰인다.
 */
@Primary
@Service
public class FallbackDashboardInsightAiService implements DashboardInsightAiService {

    private static final Logger log = LoggerFactory.getLogger(FallbackDashboardInsightAiService.class);

    private final AnthropicDashboardInsightAiService anthropicService;
    private final OpenAiDashboardInsightAiService openAiService;
    // 시간 정책(체인 예산·per-tier 타임아웃)은 DB-first 런타임 설정에서 읽는다(정적 프로퍼티 fallback).
    private final AiRuntimeSettings aiRuntimeSettings;

    public FallbackDashboardInsightAiService(AnthropicDashboardInsightAiService anthropicService,
                                             OpenAiDashboardInsightAiService openAiService,
                                             AiRuntimeSettings aiRuntimeSettings) {
        this.anthropicService = anthropicService;
        this.openAiService = openAiService;
        this.aiRuntimeSettings = aiRuntimeSettings;
    }

    @Override
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command) {
        long deadline = AiTotalTimeBudget.deadlineNanos(aiRuntimeSettings.analysisChainTotalTimeBudget());
        if (anthropicService.configured()) {
            try {
                return anthropicService.summarize(command, aiRuntimeSettings.analysisClaudeTimeout(), deadline);
            } catch (RuntimeException ex) {
                log.warn("C 대시보드 요약 Claude 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        // OpenAI tier 는 항상 시도 — 내부 Mock 폴백이 최종 안전망(never-throw)이다.
        return openAiService.summarize(command, aiRuntimeSettings.analysisOpenaiTimeout(), deadline);
    }
}
