-- =====================================================================
--  로그인 위험도(브루트포스) 잠금 정책 — 2026-07-06
--  AuthServiceImpl 의 하드코딩 상수(MAX_FAILED_LOGIN_COUNT=5, LOCK=10분)를
--  관리자 편집 정책으로 승격한다. enabled 토글 + 임계/잠금시간 편집.
--  (TripTogether login-risk 정책의 탐지 임계치 편집 축 이식)
--
--  토글 의미:
--   enabled=0(OFF): 자동 잠금 미적용(무제약) — 실패 횟수는 계속 집계하되 잠그지 않음
--   enabled=1(ON) : max_failed_count 회 실패 시 lock_minutes 분간 자동 잠금
--  기본값(enabled=1, 5회, 10분)은 기존 상수와 동일 → 도입 시 동작 무변경.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 서비스는 미적용 DB 에서도 코드 기본값으로
--     강등 로드하므로 앱 기동/로그인은 막히지 않는다.
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db) 적용 후 날짜 기록.
-- =====================================================================

CREATE TABLE IF NOT EXISTS login_risk_policy (
    id               TINYINT   NOT NULL,
    enabled          TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=무제약, 1=자동 잠금 집행',
    max_failed_count INT       NOT NULL DEFAULT 5  COMMENT '자동 잠금 트리거 연속 실패 횟수',
    lock_minutes     INT       NOT NULL DEFAULT 10 COMMENT '자동 잠금 유지 분',
    updated_by       BIGINT    NULL,
    created_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_login_risk_policy_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 단일 정책 행 시드(기본값 = 기존 상수). 재실행 안전.
INSERT INTO login_risk_policy (id, enabled, max_failed_count, lock_minutes)
VALUES (1, 1, 5, 10)
ON DUPLICATE KEY UPDATE id = id;
