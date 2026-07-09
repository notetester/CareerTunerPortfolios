package com.careertuner.collaboration.dto;

/**
 * 방 관리자 지정/해제 + 세부 권한 위임 요청.
 * manager=true 면 role 을 MANAGER 로 승격하고 지정된 권한 플래그를 부여,
 * manager=false 면 role 을 MEMBER 로 강등하고 권한을 전부 회수한다.
 * 각 권한 플래그가 null 이면 false 로 취급한다(명시적 부여만 허용).
 */
public record ConversationPermissionUpdateRequest(
        Boolean manager,
        Boolean canKick,
        Boolean canBan,
        Boolean canSetPassword,
        Boolean canInvite,
        Boolean canEditRoom,
        Boolean canManageMembers
) {
}
