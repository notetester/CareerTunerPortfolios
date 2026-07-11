package com.careertuner.applicationcase.dto;

import java.util.List;

/**
 * 지원 건 등록·재실행 화면의 단계별(OCR/공고분석/기업분석) 모델 선택지.
 * 실제 가용성 신호(자격 증명·Ollama 모델 존재·OCR 워커 엔진 준비)로 계산한다(Slice 1).
 */
public record ModelOptionsResponse(
        StageOptions ocr,
        StageOptions jobAnalysis,
        StageOptions companyAnalysis) {

    /** 한 단계의 선택지 목록과 권장 기본값. */
    public record StageOptions(
            String recommendedDefault,
            List<ProviderOption> options) {

        public StageOptions {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /**
     * provider 단위 선택지.
     *
     * @param selectable          직접 선택 가능 여부(자격 증명·가용성)
     * @param reason              선택 불가 사유(선택 가능하면 null)
     * @param autoFallbackIncluded 자동 폴백 체인 포함 여부 — OCR OpenAI 만 의미(allowed(stage)), 그 외는 null
     */
    public record ProviderOption(
            String provider,
            String displayName,
            boolean selectable,
            String reason,
            String actualModel,
            Boolean autoFallbackIncluded) {
    }
}
