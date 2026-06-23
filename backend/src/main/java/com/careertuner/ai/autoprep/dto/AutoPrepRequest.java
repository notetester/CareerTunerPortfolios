package com.careertuner.ai.autoprep.dto;

/**
 * AI 오케스트레이터 실행 요청.
 * query: 한 줄 자연어("네이버 백엔드 신입 통째로 준비해줘"). applicationCaseId/mode 는 명시 시 슬롯 파싱을 덮어쓴다(선택).
 */
public record AutoPrepRequest(
    String query,
    Long applicationCaseId,
    String mode
) {
}
