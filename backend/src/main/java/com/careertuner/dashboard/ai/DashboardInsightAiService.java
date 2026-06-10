package com.careertuner.dashboard.ai;

/**
 * 대시보드 AI 분석 결과 요약(18) 진입점.
 *
 * <p>현재 활성 구현은 {@link MockDashboardInsightAiService}(집계 기반 결정적 요약).
 * API 키 주입 시 {@link com.careertuner.dashboard.ai.prompt.DashboardInsightPromptCatalog} 를 사용하는
 * 실 LLM 구현체로 교체한다(호출부는 인터페이스에만 의존).
 */
public interface DashboardInsightAiService {

    DashboardInsightAiResult summarize(DashboardInsightAiCommand command);
}
