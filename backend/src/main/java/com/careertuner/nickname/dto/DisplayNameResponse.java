package com.careertuner.nickname.dto;

/**
 * 표시명 해석 응답 — 다른 도메인(community/collaboration)이 작성자 표시를 조회할 때 쓴다.
 *
 * <p>제재/신고/차단은 계정 단위이므로 accountId(=user_id)를 항상 함께 반환한다.
 * 표시 계층(displayName/avatarFileId)은 선택한 닉네임 프로필에서, 없으면 기본 프로필/계정명에서 온다.</p>
 */
public record DisplayNameResponse(
        Long accountId,
        Long nicknameProfileId,
        String displayName,
        Long avatarFileId,
        boolean anonymous) {
}
