package com.careertuner.nickname.dto;

/**
 * 채팅방 전용 프로필 응답.
 *
 * <p>anonymous=true 면 익명 라벨을, 아니면 지정 프로필의 닉네임을 표시명으로 쓴다.
 * 지정이 없으면(매핑 미존재) resolved=false 로 클라이언트가 기본 프로필로 폴백한다.</p>
 */
public record ConversationProfileResponse(
        Long conversationId,
        Long userId,
        Long nicknameProfileId,
        String nickname,
        boolean anonymous,
        boolean resolved) {
}
