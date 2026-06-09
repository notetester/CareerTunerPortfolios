package com.careertuner.fitanalysis.ai;

/**
 * 공고-스펙 적합도 AI 분석 진입점 (C 담당 AI 기능 12~15).
 *
 * <p>{@link OpenAiFitAnalysisAiService}가 활성 진입점이다. API 키가 없으면 결정적 mock을 사용하고,
 * {@code OPENAI_API_KEY}가 설정되면 같은 API 흐름에서 실제 구조화 분석을 실행한다.
 */
public interface FitAnalysisAiService {

    FitAnalysisAiResult generate(FitAnalysisAiCommand command);
}
