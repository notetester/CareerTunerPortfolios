package com.careertuner.admin.chatbot.dto;

import java.util.List;

/**
 * 유사도 임계값 슬라이더 미리보기 응답(F2, 읽기 전용 · §1-Q3).
 * <p>실 챗봇 컷오프(0.5 고정)를 바꾸지 않는다. 후보 임계 {@code threshold} 미만이면 "공백"으로 보는
 * 가정 아래, chatbot_response_log.top_similarity 분포에서 그 수만 읽기 전용으로 계산한다.
 *
 * @param threshold 적용해 본 후보 임계값(보정 후 실제 사용된 값)
 * @param gapCount  top_similarity &lt; threshold 인 턴 수(=이 임계라면 공백이 될 수)
 * @param total     top_similarity IS NOT NULL 인 전 턴 수(분모)
 * @param histogram 0.05 폭 버킷(0.30~0.95) 분포 — 슬라이더 위치별 변화를 한 번에 그릴 소스
 */
public record ThresholdPreviewResponse(
        double threshold,
        long gapCount,
        long total,
        List<ThresholdBucket> histogram
) {
}
