-- 구독 결제와 실제 구독 행을 연결해 환불 시 정확한 플랜과 사용권을 즉시 회수한다.
ALTER TABLE user_subscription
    ADD COLUMN payment_id BIGINT NULL AFTER id,
    ADD UNIQUE KEY uk_user_subscription_payment (payment_id),
    ADD CONSTRAINT fk_user_subscription_payment
        FOREIGN KEY (payment_id) REFERENCES payment (id) ON DELETE SET NULL;
