package com.careertuner.collaboration.dto;

import java.util.List;

/**
 * 방 설정 시트 응답 — 방 메타/공개 정책 + 멤버 상세(권한/ban 포함) + ban 목록 + 최근 활동 로그.
 * OWNER 및 위임받은 MANAGER 만 조회할 수 있다.
 */
public record ConversationSettingsResponse(
        Long conversationId,
        String type,
        String title,
        String description,
        Long imageFileId,
        String notice,
        boolean locked,
        boolean hasPassword,
        int maxMembers,
        String invitePolicy,
        boolean allowAnonymous,
        boolean anonymousOnly,
        /** 조회자 관점 권한 — 이 시트에서 무엇을 할 수 있는지(UI 게이팅용). */
        ConversationPermissionResponse myPermission,
        List<ConversationMemberDetailResponse> members,
        List<ConversationBanResponse> bans,
        List<Long> inviteAllowUserIds,
        List<ConversationAuditResponse> recentAudits
) {
}
