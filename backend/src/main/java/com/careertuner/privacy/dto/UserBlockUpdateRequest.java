package com.careertuner.privacy.dto;

import java.util.Map;

import jakarta.validation.constraints.Size;

/**
 * 계정 차단 항목 수정. flags[표면키] = "allow" | "block" | "" (빈 문자열 = 명시값 제거).
 * 예: 유저2는 1:1채팅만 차단하고 쪽지는 허용 → {"dm":"block","note":"allow"}.
 */
public record UserBlockUpdateRequest(
        Map<String, String> flags,
        Boolean blockIp,
        @Size(max = 200) String memo
) {}
