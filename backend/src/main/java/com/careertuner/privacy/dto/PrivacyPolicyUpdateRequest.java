package com.careertuner.privacy.dto;

import java.util.Map;

/**
 * 관계 정책 부분 갱신. relations[관계][표면키] = "allow" | "block" | "" (빈 문자열 = 명시값 제거, 상위 따름).
 * 포함되지 않은 키는 기존값 유지.
 */
public record PrivacyPolicyUpdateRequest(
        Map<String, Map<String, String>> relations
) {}
