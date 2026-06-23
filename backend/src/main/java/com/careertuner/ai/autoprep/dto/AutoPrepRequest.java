package com.careertuner.ai.autoprep.dto;

import java.util.List;

/**
 * AI 오케스트레이터 실행 요청.
 * query: 한 줄 자연어("네이버 백엔드 신입 통째로 준비해줘"). applicationCaseId/mode 는 명시 시 슬롯 파싱을 덮어쓴다(선택).
 * attachmentFileIds: /api/file/upload 로 올린 첨부 파일 id(선택). 플랜별 개수 게이팅(무료 1개/유료 다수)은 오케가 적용한다.
 */
public record AutoPrepRequest(
    String query,
    Long applicationCaseId,
    String mode,
    String coverLetterText,
    List<Long> attachmentFileIds
) {
}
