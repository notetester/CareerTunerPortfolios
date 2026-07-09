package com.careertuner.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CouponRedeemRequest(
        @NotBlank(message = "쿠폰 코드는 필수입니다.")
        @Size(max = 50, message = "쿠폰 코드는 50자 이하여야 합니다.")
        String code
) {
}
