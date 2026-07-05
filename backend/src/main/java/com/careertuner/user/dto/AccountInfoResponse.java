package com.careertuner.user.dto;

import java.util.List;

/**
 * 계정 정보 응답 — 마이페이지 계정 카드용.
 *
 * <p>전화번호/로그인 아이디 설정 여부, 연결된 소셜 계정(OAuth) 목록을 함께 반환한다.</p>
 */
public record AccountInfoResponse(
        Long userId,
        String email,
        String name,
        String loginId,
        boolean loginIdSet,
        String phone,
        boolean phoneVerified,
        boolean passwordEnabled,
        List<String> linkedProviders) {
}
