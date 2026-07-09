-- E 담당 크레딧 원장 테이블.
-- 하나의 AI 사용량 로그는 AI_USAGE 차감을 한 번만 만들 수 있고,
-- 이후 REFUND, ADMIN_ADJUST 같은 별도 거래 유형은 분리해서 기록할 수 있게 둔다.

CREATE TABLE IF NOT EXISTS credit_transaction (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    ai_usage_log_id BIGINT NULL,
    type            VARCHAR(30) NOT NULL,
    amount          INT NOT NULL,
    balance_after   INT NOT NULL,
    feature_type    VARCHAR(40) NULL,
    reason          VARCHAR(255) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_credit_transaction_ai_usage_type (ai_usage_log_id, type),
    KEY idx_credit_transaction_user (user_id),
    CONSTRAINT fk_credit_transaction_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_transaction_ai_usage FOREIGN KEY (ai_usage_log_id) REFERENCES ai_usage_log (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
