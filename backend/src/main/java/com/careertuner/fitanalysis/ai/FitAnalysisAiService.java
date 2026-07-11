package com.careertuner.fitanalysis.ai;

import com.careertuner.ai.common.model.RequestedAiModel;

/**
 * 공고-스펙 적합도 AI 분석 진입점 (C 담당 AI 기능 12~15).
 *
 * <p>{@link FallbackFitAnalysisAiService}(@Primary)가 활성 진입점이다. API 키가 없으면 결정적 mock을 사용하고,
 * 키가 설정되면 같은 API 흐름에서 실제 구조화 분석을 실행한다.
 */
public interface FitAnalysisAiService {

    FitAnalysisAiResult generate(FitAnalysisAiCommand command);

    /**
     * 사용자가 모델을 <b>명시 선택</b>하는 경로. 기본 구현은 {@code requestedModel} 을 무시하고 현행
     * {@link #generate(FitAnalysisAiCommand)} 로 위임한다(leaf provider 는 선택을 몰라도 됨 — 디스패처가 라우팅).
     * <b>뉴로-심볼릭 불변식</b>: 어느 모델을 골라도 판단값(fitScore/matched/missing/applyDecision)은 규칙엔진이
     * 소유해 동일하고, 설명 텍스트만 provider 에 따라 달라진다. 판단 입력 record({@link FitAnalysisAiCommand})에는
     * 모델 선택을 절대 넣지 않는다(이 별도 인자로만 전달 → 판단값 오염 격리).
     */
    default FitAnalysisAiResult generate(FitAnalysisAiCommand command, RequestedAiModel requestedModel) {
        return generate(command);
    }
}
