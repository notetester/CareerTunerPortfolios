package com.careertuner.analysis.ai;

/**
 * 장기 취업 경향 분석(16) + 다음 지원 방향 추천(17) 진입점.
 *
 * <p>현재 활성 구현은 {@link MockCareerTrendAiService}(집계 기반 결정적 요약).
 * API 키 주입 시 {@link com.careertuner.analysis.ai.prompt.CareerTrendPromptCatalog} 를 사용하는
 * 실 LLM 구현체로 교체한다(인터페이스 의존이라 호출부 변경 불필요).
 */
public interface CareerTrendAiService {

    CareerTrendAiResult generate(CareerTrendAiCommand command);
}
