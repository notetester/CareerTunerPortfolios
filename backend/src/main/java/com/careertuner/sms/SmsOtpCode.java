package com.careertuner.sms;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** sms_otp_code 테이블 VO. 전화번호 SMS OTP 인증 코드. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsOtpCode {

    private Long id;
    private Long userId;
    private String phone;
    private String code;
    private int attemptCount;
    private int maxAttempts;
    private LocalDateTime expiresAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
}
