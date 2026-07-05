package com.careertuner.nickname.dto;

/**
 * 채팅방 전용 프로필 지정 요청.
 *
 * <p>nicknameProfileId 가 null 이고 anonymous=true 면 익명 참가.
 * nicknameProfileId 가 지정되면 anonymous 는 false 로 강제된다.</p>
 */
public record ConversationProfileRequest(
        Long nicknameProfileId,
        boolean anonymous) {
}
