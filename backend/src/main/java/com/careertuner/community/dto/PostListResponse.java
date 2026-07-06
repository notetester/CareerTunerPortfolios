package com.careertuner.community.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostListResponse(
        Long id,
        String category,
        String categoryLabel,
        String title,
        String content,
        List<String> tags,
        AuthorDto author,
        StatsDto stats,
        String status,
        LocalDateTime createdAt,
        String companyName,
        String jobRole,
        /** 신고 누적으로 가려진 글(비작성자에게 블러). reportCount 가 임계 이상이면 true. */
        boolean blurred,
        int reportCount
) {

    /**
     * 작성자 표시 정보.
     * @param id 계정 id(익명이면 null — 익명성 유지). 제재/신고/차단은 이 계정 단위.
     * @param name 표시명 — 비익명은 선택한 닉네임 프로필(없으면 기본 프로필/계정명), 익명은 "익명"/익명번호.
     * @param nicknameProfileId 표시에 사용한 닉네임 프로필 id(옵션). 익명이거나 프로필 미해석이면 null.
     * @param isAnonymous 익명 작성 여부.
     */
    public record AuthorDto(Long id, String name, Long nicknameProfileId, boolean isAnonymous) {}

    public record StatsDto(int viewCount, int commentCount, int likeCount, int dislikeCount,
                           int recommendCount, int disrecommendCount, int bookmarkCount, int scrapCount) {}
}
