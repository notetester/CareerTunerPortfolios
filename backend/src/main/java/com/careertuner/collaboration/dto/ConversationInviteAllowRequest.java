package com.careertuner.collaboration.dto;

import java.util.List;

/** SPECIFIC_MEMBERS 초대 정책의 허용 멤버 목록 설정 요청(전체 교체). */
public record ConversationInviteAllowRequest(
        List<Long> userIds
) {
}
