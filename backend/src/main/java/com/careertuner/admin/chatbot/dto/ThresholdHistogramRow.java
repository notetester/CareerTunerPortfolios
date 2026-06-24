package com.careertuner.admin.chatbot.dto;

import lombok.Data;

/**
 * 임계값 미리보기 히스토그램 raw 행(MyBatis 매핑용 — record 대신 @Data).
 * <p>bucket = top_similarity 를 0.05 폭으로 내림한 버킷 인덱스(예: 0.30~0.35 → 6, 0.78 → 15).
 * 데이터 있는 버킷만 반환 → 서비스에서 전 구간(0.30~0.95) 빈 칸 0 채움.
 */
@Data
public class ThresholdHistogramRow {
    private int bucket;
    private long count;
}
