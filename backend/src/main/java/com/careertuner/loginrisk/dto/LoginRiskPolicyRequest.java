package com.careertuner.loginrisk.dto;

/** 로그인 위험도 잠금 정책 편집 요청(부분 갱신 — null 이면 기존값 유지). */
public record LoginRiskPolicyRequest(
        Boolean enabled,
        Integer maxFailedCount,
        Integer lockMinutes,
        String reason
) {}
