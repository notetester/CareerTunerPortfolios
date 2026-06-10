-- C 영역 패치(2026-06-10): 대시보드 "오늘의 할 일" 완료 처리/사용자 할 일 저장 테이블.
-- 디자인 분석 §6.4 권장 카드의 "오늘의 할 일 — 완료 처리" 액션을 지원한다.
-- derived_key가 NULL이면 사용자가 직접 추가한 할 일, 값이 있으면 파생(자동 계산) 할 일의 완료 오버라이드.
-- 멱등: CREATE TABLE IF NOT EXISTS 만 사용한다.

CREATE TABLE IF NOT EXISTS dashboard_todo (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    derived_key  VARCHAR(120) NULL,
    task         VARCHAR(500) NOT NULL,
    time_label   VARCHAR(50)  NOT NULL DEFAULT '오늘',
    done         TINYINT(1)   NOT NULL DEFAULT 0,
    completed_at DATETIME     NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dashboard_todo_derived (user_id, derived_key),
    KEY idx_dashboard_todo_user (user_id, created_at),
    CONSTRAINT fk_dashboard_todo_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
