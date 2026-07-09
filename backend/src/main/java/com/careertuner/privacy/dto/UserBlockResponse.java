package com.careertuner.privacy.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 계정 차단 항목. flags 는 명시 설정만(비어 있으면 전 표면이 blockedAccount 정책=기본 차단을 따름).
 * masked=true(익명 콘텐츠 기반 차단)면 blockedUserName 은 masked_label, blockedUserEmail 은 null 로 마스킹된다.
 */
public record UserBlockResponse(
        Long id,
        Long blockedUserId,
        String blockedUserName,
        String blockedUserEmail,
        boolean masked,
        Map<String, String> flags,
        boolean blockIp,
        String memo,
        LocalDateTime createdAt
) {}
