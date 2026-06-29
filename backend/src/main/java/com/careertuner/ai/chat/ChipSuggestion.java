package com.careertuner.ai.chat;

/**
 * quickReply(좁혀가기 칩) 후보 1건. 모델이 매긴 두 점수를 서버 선별에만 쓰고,
 * 외부 응답(quickReplies)에는 {@code text} 만 매핑한다(외부 인터페이스 String 유지).
 *
 * @param text       칩 문구(사용자에게 보일 짧은 한국어)
 * @param relevance  지금 대화 흐름과의 관련성 0~100
 * @param importance 사용자 목표 진전 중요도 0~100
 */
public record ChipSuggestion(String text, int relevance, int importance) {

    /**
     * 선별 정렬용 가중 점수. relevance(흐름 적합)에 가중을 더 둔다.
     * ★절대 임계값은 쓰지 않는다(8B 절대점수 불안정 → 칩 깜빡임). 상대 컷만 쓴다.
     */
    public double score() {
        return relevance * 0.7 + importance * 0.3;
    }
}
