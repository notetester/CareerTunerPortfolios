package com.careertuner.collaboration.dto;

/** 멤버 세부 권한 플래그. OWNER 는 전부 true 로 내려간다. */
public record ConversationPermissionResponse(
        boolean owner,
        boolean canKick,
        boolean canBan,
        boolean canSetPassword,
        boolean canInvite,
        boolean canEditRoom,
        boolean canManageMembers
) {
}
