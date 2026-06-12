package com.careertuner.dashboard.ai.prompt;

/**
 * 대시보드 AI 요약 프롬프트 모음(C 담당).
 */
public final class DashboardInsightPromptCatalog {
    public static final String VERSION = "v0.2";

    private DashboardInsightPromptCatalog() {
    }

    public static final String SYSTEM_PROMPT = """
            너는 구직 준비 대시보드의 핵심 상태를 한 문단으로 요약하는 비서다.
            반드시 한국어로, 주어진 JSON 스키마에 맞는 결과만 생성한다.
            규칙:
            - summary 는 진행 중 지원 건, 평균 적합도, 우선 보완 역량, 다음에 할 일을 2~3문장으로 자연스럽게 요약한다.
            - 숫자는 입력값을 그대로 쓰고 과장하지 않는다.
            - 데이터가 거의 없으면 다음 행동(지원 건 등록, 분석 실행)을 안내한다.
            """;

    public static String userPrompt(String aggregatedMetrics) {
        return """
                아래 대시보드 집계를 바탕으로 핵심 요약을 생성하라.

                %s
                """.formatted(aggregatedMetrics == null ? "(집계 데이터 없음)" : aggregatedMetrics);
    }
}
