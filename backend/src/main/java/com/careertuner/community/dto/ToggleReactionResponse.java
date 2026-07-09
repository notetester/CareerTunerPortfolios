package com.careertuner.community.dto;

/**
 * 리액션 토글 결과 — 같은 축 교체가 있어 클라이언트 델타 계산 대신
 * 서버가 토글 후의 카운트 전체를 내려준다(응답 기반 UI 갱신).
 */
public record ToggleReactionResponse(
        boolean active,
        String reactionType,
        CountsDto counts
) {
    /** 대상(글/댓글)의 토글 후 카운트. 댓글은 bookmark/scrap 0 고정. */
    public record CountsDto(
            int likeCount,
            int dislikeCount,
            int recommendCount,
            int disrecommendCount,
            int bookmarkCount,
            int scrapCount
    ) {}
}
