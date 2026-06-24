package com.careertuner.ai.autoprep;

/**
 * AI 오케스트레이터 슬롯 — 한 줄 요청에서 파싱하거나 인테이크 챗봇이 채운 실행 조건.
 * 자체 LLM(또는 폴백)이 자연어에서 추출한다.
 */
public record PrepSlots(
    String company,
    String jobTitle,
    String mode,
    Long applicationCaseId
) {
    /** 면접 진행에 필요한 핵심 슬롯(지원 건)이 채워졌는지. */
    public boolean hasCase() {
        return applicationCaseId != null;
    }
}
