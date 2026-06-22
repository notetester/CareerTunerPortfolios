-- 댓글 AI 검열 결과 저장 테이블.
-- post_ai_result 구조 복제(대상만 comment_id). 조회 경로(getComments)와는 절대 조인하지 않고,
-- 관리자 검토 목록·배치 재검열 선정(NOT EXISTS)에만 사용한다. 검열에 의한 표시 차단은
-- community_comment.status='HIDDEN' flip 으로만 이뤄진다(이 테이블은 감사/이력 전용).
CREATE TABLE IF NOT EXISTS comment_ai_result (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    comment_id    BIGINT        NOT NULL,
    task_type     VARCHAR(30)   NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    result_json   JSON          NULL,
    model         VARCHAR(80)   NULL,
    error_message VARCHAR(1000) NULL,
    attempt_count INT           NOT NULL DEFAULT 0,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at  DATETIME      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_ai_result_task (comment_id, task_type),
    KEY idx_comment_ai_result_status (task_type, status, completed_at),
    CONSTRAINT fk_comment_ai_result_comment
        FOREIGN KEY (comment_id) REFERENCES community_comment (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
