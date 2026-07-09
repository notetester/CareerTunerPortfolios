package com.careertuner.collaboration.dto;

import jakarta.validation.constraints.NotNull;

public record FriendRequestCreateRequest(
        @NotNull Long targetUserId
) {
}
