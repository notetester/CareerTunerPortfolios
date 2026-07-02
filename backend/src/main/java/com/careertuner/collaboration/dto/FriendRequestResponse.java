package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record FriendRequestResponse(
        Long id,
        UserBriefResponse requester,
        UserBriefResponse receiver,
        String status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {
}
