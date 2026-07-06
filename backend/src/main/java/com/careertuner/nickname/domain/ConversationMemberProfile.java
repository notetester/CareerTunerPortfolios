package com.careertuner.nickname.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 채팅방 전용 닉네임 프로필 매핑 VO (conversation_member_profile).
 *
 * <p>collaboration 스키마를 수정하지 않기 위한 별도 매핑 테이블.
 * nickname_profile_id 가 NULL 이면 익명 참가.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberProfile {

    private Long conversationId;
    private Long userId;
    private Long nicknameProfileId;
    private boolean anonymous;
    private LocalDateTime updatedAt;
}
