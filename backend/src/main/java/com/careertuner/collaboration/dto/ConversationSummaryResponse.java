package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long id,
        String type,
        String title,
        String description,
        String displayName,
        Long imageFileId,
        String notice,
        boolean locked,
        int memberCount,
        boolean joined,
        boolean muted,
        /** 뷰어의 방 내 role (OWNER/MANAGER/MEMBER, 미참여 null). */
        String myRole,
        /** 뷰어가 방 설정 시트에 진입 가능한지(OWNER 또는 권한 위임 MANAGER). */
        boolean canManageRoom,
        UserBriefResponse peer,
        MessagePreviewResponse latestMessage,
        int unreadCount,
        LocalDateTime updatedAt
) {
}
