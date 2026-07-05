package com.careertuner.community.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.community.domain.PostScrap;

/** 스크랩 항목 — 스냅샷 기반이라 원본이 사라져도 열람 가능하다. */
public record ScrapResponse(
        Long id,
        Long postId,
        String title,
        String content,
        String authorLabel,
        String category,
        boolean anonymous,
        LocalDateTime scrappedAt,
        /** 원본 글이 아직 열람 가능한지(PUBLISHED). false 면 "원본이 삭제된 글" 배지. */
        boolean originAvailable
) {

    public static ScrapResponse from(PostScrap scrap) {
        boolean available = scrap.getPostId() != null && "PUBLISHED".equals(scrap.getOriginStatus());
        return new ScrapResponse(
                scrap.getId(),
                scrap.getPostId(),
                scrap.getSnapshotTitle(),
                scrap.getSnapshotContent(),
                scrap.getSnapshotAuthorLabel(),
                scrap.getSnapshotCategory(),
                scrap.isAnonymous(),
                scrap.getScrappedAt(),
                available
        );
    }

    public record Page(List<ScrapResponse> items, int total, int page, int size) {}
}
