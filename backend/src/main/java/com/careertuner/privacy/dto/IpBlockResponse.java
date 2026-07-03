package com.careertuner.privacy.dto;

import java.time.LocalDateTime;

/** IP 차단 항목 — 원본 IP 는 절대 노출하지 않고 라벨과 일치 계정 수만 보여준다. */
public record IpBlockResponse(
        Long id,
        String label,
        Long sourceUserId,
        String sourceUserName,
        int matchedAccounts,
        LocalDateTime createdAt
) {}
