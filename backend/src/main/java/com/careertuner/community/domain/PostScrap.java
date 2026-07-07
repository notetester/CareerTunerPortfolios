package com.careertuner.community.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 게시글 스크랩 — 스크랩 시점 스냅샷을 보존한다(원본 수정/삭제와 무관하게 열람 가능).
 * 즐겨찾기(BOOKMARK 리액션, 링크형)와 별개 기능.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostScrap {

    private Long id;
    private Long userId;
    /** 원본 글 링크. 원본이 하드삭제되면 NULL(스냅샷은 유지). */
    private Long postId;
    private String snapshotTitle;
    private String snapshotContent;
    /** 스크랩 시점 작성자 표시명(익명 글이면 "익명"). */
    private String snapshotAuthorLabel;
    private String snapshotCategory;
    /** 익명 스크랩 — 타인 시점 목록 제외, 집계 포함. */
    private boolean anonymous;
    private LocalDateTime scrappedAt;

    // JOIN 파생 — 원본 글 현재 상태(NULL 이면 원본 소실)
    private String originStatus;
}
