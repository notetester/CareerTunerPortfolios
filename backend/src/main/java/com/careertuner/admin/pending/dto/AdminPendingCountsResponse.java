package com.careertuner.admin.pending.dto;

/**
 * 관리자 사이드바 "미처리 큐" 카운트 + 색 판정 응답.
 * <p>신고·자동숨김은 같은 /admin/community 메뉴지만 성격이 달라 분리해서 센다(합산 X).
 */
public record AdminPendingCountsResponse(
        QueueBadge reports,        // 신고 PENDING (post_report + comment_report)
        QueueBadge hiddenPosts,    // 자동숨김 게시글 (community_post HIDDEN)
        QueueBadge hiddenComments, // 자동숨김 댓글 (community_comment HIDDEN)
        QueueBadge tickets         // 미응답 티켓 (support_ticket RECEIVED)
) {

    /**
     * 큐 뱃지 하나.
     *
     * @param count    미처리 건수
     * @param severity 색 판정: "RED"(급함) | "YELLOW"(주의) | "NONE"(0건)
     */
    public record QueueBadge(int count, String severity) {

        public static final String RED = "RED";
        public static final String YELLOW = "YELLOW";
        public static final String NONE = "NONE";

        /** 0건이면 NONE, 아니면 red 여부로 RED/YELLOW. */
        public static QueueBadge of(int count, boolean red) {
            if (count <= 0) {
                return new QueueBadge(0, NONE);
            }
            return new QueueBadge(count, red ? RED : YELLOW);
        }
    }
}
