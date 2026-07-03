package com.careertuner.privacy.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** 계정 차단 항목. flags 는 명시 설정만(비어 있으면 전 표면이 blockedAccount 정책=기본 차단을 따름). */
public record UserBlockResponse(
        Long id,
        Long blockedUserId,
        String blockedUserName,
        String blockedUserEmail,
        Map<String, String> flags,
        boolean blockIp,
        String memo,
        LocalDateTime createdAt
) {}
