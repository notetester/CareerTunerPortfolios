package com.careertuner.correction.dto;

import java.time.LocalDateTime;

/** D 면접 답변을 E 첨삭 입력으로 넘길 때 사용하는 소유권 검증 완료 스냅샷. */
public record CorrectionInterviewSourceResponse(
        Long sourceRefId,
        Long applicationCaseId,
        Long sessionId,
        Long questionId,
        String questionText,
        String originalText,
        Integer score,
        String feedback,
        LocalDateTime answeredAt
) {
}
