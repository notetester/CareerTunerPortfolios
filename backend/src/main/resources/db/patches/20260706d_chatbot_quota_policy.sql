-- =====================================================================
--  AI 챗봇 일일 사용 쿼터 정책 — 2026-07-06
--  로그인 사용자의 하루 챗봇 질문 수 상한. enabled 토글 + 일일 한도 편집.
--  (TripTogether assistant 등급별 쿼터의 도메인 중립 축 이식)
--
--  토글 의미:
--   enabled=0(OFF): 무제약(쿼터 미적용) — 현재 동작. 기본값이라 도입 시 변화 없음.
--   enabled=1(ON) : 오늘 사용량이 daily_limit 이상이면 429 로 차단.
--   ※ 비로그인(익명)은 per-user 집계 불가라 쿼터 미적용.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 서비스는 미적용 DB 에서도 코드 기본값(OFF)으로
--     강등 로드 → 앱 기동·챗봇 정상.
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db) 적용 후 날짜 기록.
-- =====================================================================

CREATE TABLE IF NOT EXISTS chatbot_quota_policy (
    id          TINYINT   NOT NULL,
    enabled     TINYINT(1) NOT NULL DEFAULT 0  COMMENT '0=무제약, 1=일일 쿼터 집행',
    daily_limit INT       NOT NULL DEFAULT 100 COMMENT '로그인 사용자 1인 하루 허용 질문 수',
    updated_by  BIGINT    NULL,
    created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_chatbot_quota_policy_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 단일 정책 행 시드(기본 OFF = 현재 동작). 재실행 안전.
INSERT INTO chatbot_quota_policy (id, enabled, daily_limit)
VALUES (1, 0, 100)
ON DUPLICATE KEY UPDATE id = id;
