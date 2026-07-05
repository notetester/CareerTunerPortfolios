package com.careertuner.collaboration.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 방 활동 로그 항목 (누가 무엇을). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationAuditRow {

    private Long id;
    private Long conversationId;
    private Long actorId;
    private String actorName;
    private Long targetUserId;
    private String targetName;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
