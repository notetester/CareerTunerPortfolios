package com.careertuner.auth.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** email_verification 테이블 VO. 이메일 인증/아이디 찾기/비밀번호 재설정 토큰. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerification {

    private Long id;
    private Long userId;
    private String email;
    private String token;
    private String purpose;           // VERIFY/EMAIL_CHANGE/RESET_PW/FIND_ID/DORMANT_RELEASE
    private LocalDateTime expiredAt;
    private boolean used;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
