package com.careertuner.nickname.dto;

/**
 * 표시명 해석 응답 — 다른 도메인(community/collaboration)이 작성자 표시를 조회할 때 쓴다.
 *
 * <p>제재/신고/차단은 계정 단위이므로 활성 계정은 accountId(=user_id)를 함께 반환한다.
 * 탈퇴 계정은 공개 화면에서 다시 연결할 수 없도록 accountId와 프로필 식별자를 null로 반환한다.
 * 표시 계층(displayName/avatarFileId)은 선택한 닉네임 프로필에서, 없으면 기본 프로필/계정명에서 온다.</p>
 */
public record DisplayNameResponse(
        Long accountId,
        Long nicknameProfileId,
        String displayName,
        Long avatarFileId,
        boolean anonymous) {
}
