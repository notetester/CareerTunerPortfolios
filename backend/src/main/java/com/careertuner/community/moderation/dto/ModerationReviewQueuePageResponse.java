package com.careertuner.community.moderation.dto;

import java.util.List;

public record ModerationReviewQueuePageResponse(
        List<ModerationReviewQueueItemResponse> items,
        int total,
        int page,
        int size,
        boolean hasNext
) {}
