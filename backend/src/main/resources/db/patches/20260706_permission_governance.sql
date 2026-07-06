-- 관리자 권한 거버넌스: 셀프서비스 권한 요청 → 슈퍼관리자 승인/거절 워크플로우.
-- TripTogether ADMIN_PERMISSION 요청/승인 컬럼 개념을 전용 요청 테이블로 이식.

CREATE TABLE IF NOT EXISTS admin_permission_request (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '권한을 받을 대상 관리자',
    permission_code VARCHAR(80)  NOT NULL COMMENT '요청 권한 코드',
    description     VARCHAR(500) NULL COMMENT '요청 사유',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    requested_by    BIGINT       NULL COMMENT '요청자',
    decided_by      BIGINT       NULL COMMENT '승인/거절 처리자',
    decided_at      DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_permission_request_status (status, created_at),
    KEY idx_admin_permission_request_user (user_id, created_at),
    CONSTRAINT fk_admin_permission_request_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_permission_request_code FOREIGN KEY (permission_code) REFERENCES admin_permission_policy (permission_code) ON DELETE CASCADE,
    CONSTRAINT fk_admin_permission_request_requested_by FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_admin_permission_request_decided_by FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
