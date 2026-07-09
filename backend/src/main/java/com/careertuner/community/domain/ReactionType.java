package com.careertuner.community.domain;

/**
 * 커뮤니티 리액션 종류 — 용도별 2축 + 즐겨찾기.
 * <ul>
 *   <li>RECOMMEND/DISRECOMMEND — 추천/비추천 축(트렌드·인기글 산정용)</li>
 *   <li>LIKE/DISLIKE — 좋아요/싫어요 축(개인화용)</li>
 *   <li>BOOKMARK — 즐겨찾기(링크형 — 원본 삭제 시 함께 소멸. 스냅샷 보존은 post_scrap)</li>
 * </ul>
 * 같은 축 안에서는 반대 리액션 클릭 시 교체, 같은 것 재클릭 시 취소(토글)된다.
 */
public enum ReactionType {
    RECOMMEND(Axis.RECOMMEND_AXIS),
    DISRECOMMEND(Axis.RECOMMEND_AXIS),
    LIKE(Axis.PREFERENCE),
    DISLIKE(Axis.PREFERENCE),
    BOOKMARK(Axis.BOOKMARK);

    /** 리액션 축 — UNIQUE (user, target, axis) 의 세 번째 키. */
    public enum Axis { RECOMMEND_AXIS, PREFERENCE, BOOKMARK }

    private final Axis axis;

    ReactionType(Axis axis) {
        this.axis = axis;
    }

    public Axis axis() {
        return axis;
    }
}
