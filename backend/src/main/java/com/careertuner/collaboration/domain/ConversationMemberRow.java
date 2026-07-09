package com.careertuner.collaboration.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 메시지 알림 fan-out 용 대화방 멤버(이름·음소거 포함). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberRow {

    private Long userId;
    private String name;
    private Boolean muted;
}
