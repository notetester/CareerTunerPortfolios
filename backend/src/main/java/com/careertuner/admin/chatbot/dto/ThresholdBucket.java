package com.careertuner.admin.chatbot.dto;

/**
 * 유사도 임계값 미리보기 히스토그램의 한 구간(F2). 폭 0.05 버킷.
 * <p>구간은 [from, to) 반열린(from 이상, to 미만). 슬라이더 위치별 공백 변화를 한 번에 그릴 수 있게
 * 전 구간(0.30~0.95)을 빈 칸 0 으로 채워 연속 막대로 준다.
 *
 * @param from  버킷 하한(이상)
 * @param to    버킷 상한(미만)
 * @param count 이 구간에 속한 턴 수(top_similarity IS NOT NULL 대상)
 */
public record ThresholdBucket(double from, double to, long count) {
}
