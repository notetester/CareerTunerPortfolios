package com.careertuner.collaboration.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 재입장불가 강퇴(ban) 명단 항목. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationBanRow {

    private Long userId;
    private String name;
    private String email;
    private Long bannedBy;
    private String reason;
    private LocalDateTime createdAt;
}
