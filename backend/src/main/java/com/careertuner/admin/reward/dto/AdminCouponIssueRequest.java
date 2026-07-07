package com.careertuner.admin.reward.dto;

import jakarta.validation.constraints.NotNull;

/** 관리자가 특정 사용자에게 쿠폰을 발급. */
public record AdminCouponIssueRequest(
        @NotNull Long userId
) {
}
