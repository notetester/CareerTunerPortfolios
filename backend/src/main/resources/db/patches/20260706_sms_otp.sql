-- =====================================================================
--  전화번호 SMS OTP 인증 (2026-07-06)
--  - sms_otp_code: 로그인 사용자가 전화번호 소유를 검증할 때 사용하는 6자리 OTP.
--    실 제공자 키(twilio/aligo/naver-sens)가 있으면 실 발송,
--    없으면 Mock 제공자가 코드를 로깅하고 devCode 로 응답에 담아 데모를 완결한다.
--  - users.phone / users.phone_verified 컬럼은 이미 존재하므로 검증 성공 시 갱신만 한다.
-- =====================================================================

CREATE TABLE IF NOT EXISTS sms_otp_code (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT '인증을 요청한 회원',
    phone         VARCHAR(40)  NOT NULL COMMENT '인증 대상 전화번호',
    code          VARCHAR(10)  NOT NULL COMMENT '발송한 6자리 OTP 코드',
    attempt_count INT          NOT NULL DEFAULT 0 COMMENT '검증 시도 횟수',
    max_attempts  INT          NOT NULL DEFAULT 5 COMMENT '허용 최대 검증 시도 횟수',
    expires_at    DATETIME     NOT NULL COMMENT '코드 만료 시각',
    verified_at   DATETIME     NULL COMMENT '검증 성공 시각. NULL이면 미검증',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sms_otp_user_phone (user_id, phone),
    KEY idx_sms_otp_expires (expires_at),
    CONSTRAINT fk_sms_otp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '전화번호 SMS OTP 인증 코드';
