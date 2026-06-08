package com.careertuner.fitanalysis.ai;

/**
 * 공고-스펙 적합도 AI 분석 진입점 (C 담당 AI 기능 12~15).
 *
 * <p>현재 활성 구현은 {@link MockFitAnalysisAiService}로, API 키 없이 결정적 mock 결과를 만든다.
 * 실제 LLM 연동 시:
 * <ol>
 *   <li>{@code careertuner.openai.api-key} 가 설정되면(공통 {@code OpenAiProperties.configured()})
 *       동작하는 {@code OpenAiFitAnalysisAiService} 를 이 인터페이스 구현체로 추가하고,</li>
 *   <li>{@link com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog} 의 시스템 프롬프트와
 *       JSON 스키마를 사용해 구조화 응답을 받은 뒤 {@link FitAnalysisAiResult} 로 매핑하고,</li>
 *   <li>실 구현에 {@code @Primary} 를 주거나 mock 에 조건부 비활성화를 두어 교체한다.</li>
 * </ol>
 * 서비스 호출부({@code FitAnalysisService.generate})는 이 인터페이스에만 의존하므로 구현 교체만으로 실 AI가 켜진다.
 */
public interface FitAnalysisAiService {

    FitAnalysisAiResult generate(FitAnalysisAiCommand command);
}
