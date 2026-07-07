package com.careertuner.privacy.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 개인 채팅방 차단 1건 — 방 숨김 + 재초대/구성원 경유 초대 차단 파생 규칙. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationBlock {

    private Long id;
    private Long userId;
    private Long conversationId;
    private String flagsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // JOIN 표시용
    private String conversationTitle;
    private String conversationType;
}
