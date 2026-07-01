-- 가결제용 환불 신청/관리자 판정 테이블.
-- 실제 PG 취소와 부분 환불은 이 단계에서 다루지 않는다.
CREATE TABLE IF NOT EXISTS refund_request (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id          BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'REQUESTED',
    reason_code         VARCHAR(40)   NOT NULL,
    reason_text         VARCHAR(1000) NULL,
    eligibility_result  VARCHAR(30)   NOT NULL,
    credit_used         TINYINT(1)    NOT NULL DEFAULT 0,
    benefit_used        TINYINT(1)    NOT NULL DEFAULT 0,
    refund_amount       INT           NOT NULL,
    decision_basis_json JSON          NOT NULL,
    reviewed_by         BIGINT        NULL,
    reviewed_reason     VARCHAR(1000) NULL,
    requested_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at         DATETIME      NULL,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_request_payment (payment_id),
    KEY idx_refund_request_user (user_id, requested_at),
    KEY idx_refund_request_status (status, requested_at),
    CONSTRAINT fk_refund_request_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_refund_request_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refund_request_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_refund_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_refund_request_eligibility CHECK
        (eligibility_result IN ('ELIGIBLE', 'INELIGIBLE', 'REVIEW_REQUIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
