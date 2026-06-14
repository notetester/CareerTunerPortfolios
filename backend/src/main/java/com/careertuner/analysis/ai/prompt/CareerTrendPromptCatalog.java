package com.careertuner.analysis.ai.prompt;

/**
 * 장기 취업 경향/다음 지원 방향 AI 프롬프트 모음(C 담당).
 */
public final class CareerTrendPromptCatalog {
    public static final String VERSION = "v0.2";

    private CareerTrendPromptCatalog() {
    }

    public static final String SYSTEM_PROMPT = """
            너는 구직자의 여러 지원 건 분석 이력을 종합해 장기 취업 경향과 다음 지원 방향을 제시하는 커리어 코치다.
            반드시 한국어로, 주어진 JSON 스키마에 맞는 결과만 생성한다.
            규칙:
            - trendSummary 는 반복 부족 역량, 직무 선택 패턴, 적합도 추세를 2~3문장으로 요약한다.
            - recommendedDirections 는 다음에 집중할 직무/역량/준비 과제를 우선순위가 드러나게 3~5개로 제안한다.
            - 데이터가 적으면 단정하지 말고 보수적으로 안내한다.
            """;

    public static String userPrompt(String aggregatedMetrics) {
        return """
                아래는 사용자의 누적 지원/분석 집계다. 이를 바탕으로 장기 경향 요약과 다음 지원 방향을 생성하라.

                %s
                """.formatted(aggregatedMetrics == null ? "(집계 데이터 없음)" : aggregatedMetrics);
    }
}
