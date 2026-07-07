package com.careertuner.collaboration.dto;

import java.util.List;

/** 관리자 방 상세 — 멤버·밴·활동 로그 오버사이트(권한 게이팅 없이 운영자 관점 전체). */
public record AdminConversationDetailResponse(
        Long conversationId,
        String type,
        String title,
        String description,
        String notice,
        boolean locked,
        String invitePolicy,
        boolean allowAnonymous,
        boolean anonymousOnly,
        List<ConversationMemberDetailResponse> members,
        List<ConversationBanResponse> bans,
        List<ConversationAuditResponse> audits
) {
}
