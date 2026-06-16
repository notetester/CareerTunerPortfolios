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
        String jobRole
) {

    public record AuthorDto(Long id, String name, boolean isAnonymous) {}

    public record StatsDto(int viewCount, int commentCount, int likeCount, int bookmarkCount) {}
}
