package com.careertuner.collaboration.dto;

import jakarta.validation.constraints.NotNull;

/** 대화방 알림 음소거 on/off 요청. */
public record ConversationMuteRequest(
        @NotNull Boolean muted
) {}
