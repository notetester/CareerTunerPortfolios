-- =====================================================================
--  F 담당(커뮤니티) 스키마 마이그레이션 — 2026-06-12
--  AI 검열 설정 테이블 추가. 엄격도(STRICT/NORMAL/LENIENT)와
--  자동 숨김 임계값(hide_threshold)을 단일 행으로 관리한다.
--
--  배경:
--   게시글 AI 검열 파이프라인(Ollama gemma4)의 판정 기준을
--   관리자가 런타임에 조절할 수 있도록 설정 테이블을 분리.
--   ModerationSettingService가 volatile 캐싱으로 읽으며,
--   변경 시 즉시 전체 검열에 반영된다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. CREATE TABLE IF NOT EXISTS 라 재실행 안전.
--  실행: mysql -h <host> -u <user> -p <db> < 20260612_f_ai_moderation_setting.sql
--
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ 54.116.80.214) 적용 후 날짜 기록할 것.
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

CREATE TABLE IF NOT EXISTS ai_moderation_setting (
    id             TINYINT      NOT NULL,
    strictness     VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    hide_threshold DECIMAL(3,2) NOT NULL DEFAULT 0.80,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_hide_threshold CHECK (hide_threshold BETWEEN 0.50 AND 0.95)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 기본 행 삽입 (NORMAL / 0.80)
INSERT INTO ai_moderation_setting (id) VALUES (1)
ON DUPLICATE KEY UPDATE id = id;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW TABLES LIKE 'ai_moderation_setting';
-- SELECT * FROM ai_moderation_setting;
