package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record FriendResponse(
        UserBriefResponse user,
        LocalDateTime friendsSince
) {
}
