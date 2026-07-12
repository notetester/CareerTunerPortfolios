package com.careertuner.collaboration.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationMessage {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String kind;
    private String content;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private String senderName;
    private String senderEmail;
    private String senderStatus;

    // ── 표시명 해석용 방 전용 프로필/익명 정보(JOIN) ──
    /** 이 방에서 발신자가 익명 참가인지(collaboration_conversation_member.anonymous). */
    private Boolean senderAnonymous;
    /** 익명 참가 시 방 전용 표시 닉네임(collaboration_conversation_member.room_nickname). NULL 가능. */
    private String senderRoomNickname;
    /** 방 전용 닉네임 프로필 매핑(conversation_member_profile.nickname_profile_id). NULL 이면 매핑 없음. */
    private Long senderNicknameProfileId;
}
