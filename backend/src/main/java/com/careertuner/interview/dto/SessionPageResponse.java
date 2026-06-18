package com.careertuner.interview.dto;

import java.util.List;

/**
 * 최근 면접 기록 페이지 응답 — 세션 목록 + 페이지 메타.
 *
 * <p>알림/모더레이션의 {@code *PageResponse}와 동일한 형태(items, total, page, size, hasNext)로 맞춰
 * 프런트 "더보기" 누적 페이징에 그대로 쓴다.
 */
public record SessionPageResponse(
        List<InterviewSessionResponse> sessions,
        int total,
        int page,
        int size,
        boolean hasNext
) {}
