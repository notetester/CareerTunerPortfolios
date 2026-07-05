package com.careertuner.collaboration.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 방 관리자(MANAGER) 세부 권한 플래그.
 * OWNER 는 전권이라 이 행 없이도 전부 true 로 취급하고, MANAGER 만 이 행의 플래그로 제한한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationPermissionRow {

    private Long conversationId;
    private Long userId;
    private Boolean canKick;
    private Boolean canBan;
    private Boolean canSetPassword;
    private Boolean canInvite;
    private Boolean canEditRoom;
    private Boolean canManageMembers;
    private Long grantedBy;
}
