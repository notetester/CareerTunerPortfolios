package com.careertuner.collaboration.dto;

import jakarta.validation.constraints.Size;

/** 재입장불가 강퇴(ban) 요청. */
public record ConversationBanRequest(
        @Size(max = 500) String reason
) {
}
