package com.careertuner.collaboration.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 멤버 목록 상세 — role·세부 권한·ban 여부·익명/방 전용 프로필을 함께 노출한다.
 * 방 설정 시트의 멤버·권한 탭에서 사용.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberDetailRow {

    private Long userId;
    private String name;
    private String email;
    private String role;
    private String status;
    private Boolean anonymous;
    private String roomNickname;
    private Long roomProfileFileId;
    private LocalDateTime joinedAt;
    // 세부 권한 플래그 (permission 행이 없으면 null → 서비스에서 false 로 해석)
    private Boolean canKick;
    private Boolean canBan;
    private Boolean canSetPassword;
    private Boolean canInvite;
    private Boolean canEditRoom;
    private Boolean canManageMembers;
    // ban 여부 (LEFT JOIN — ban 행 있으면 true)
    private Boolean banned;
}
