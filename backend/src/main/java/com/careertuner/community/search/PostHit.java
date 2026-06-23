package com.careertuner.community.search;

/**
 * 커뮤니티 검색 결과 1건. url 은 프론트 라우트(`/community/posts/{id}`)와 일치 — 링크 접지의 원천.
 * 챗봇 툴(searchCommunityPosts)의 반환 타입.
 */
public record PostHit(Long postId, String title, String url, String snippet) {}
