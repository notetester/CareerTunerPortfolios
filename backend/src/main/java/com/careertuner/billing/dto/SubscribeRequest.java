package com.careertuner.billing.dto;

import jakarta.validation.constraints.NotBlank;

/** 구독 신청. cycle 은 MONTHLY|YEARLY(미지정 시 MONTHLY). */
public record SubscribeRequest(
        @NotBlank(message = "요금제를 선택해 주세요.")
        String planCode,
        String cycle
) {}
