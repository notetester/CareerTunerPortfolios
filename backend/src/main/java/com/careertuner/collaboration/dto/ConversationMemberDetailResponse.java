package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;

/** 방 설정 멤버·권한 탭의 멤버 행 — role·세부 권한·ban 여부·익명/방 전용 프로필. */
public record ConversationMemberDetailResponse(
        Long userId,
        /** 익명 참가자는 마스킹된 방 전용 표시명, 그 외 실명. */
        String displayName,
        /** 익명이면 null(실명·이메일 비노출). */
        String email,
        String role,
        boolean anonymous,
        Long roomProfileFileId,
        LocalDateTime joinedAt,
        ConversationPermissionResponse permission,
        boolean banned
) {
}
