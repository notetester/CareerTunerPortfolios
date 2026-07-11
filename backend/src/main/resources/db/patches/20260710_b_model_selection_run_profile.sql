-- 지원 건별 모델 선택·재실행 Phase 1 — B 소유 테이블 스키마.
-- 초기 실행 프로필(중복 진입 방지 + 늦은 완료 fencing), 단계별 선택/실제 모델 실행 메타데이터.
-- 추가 컬럼은 전부 NULL 허용 → 기존 행은 NULL(=unknown, "폴백 미사용"으로 오해 금지).
-- information_schema 가드로 idempotent(재실행 안전). 공통 ai_usage_log 는 변경하지 않는다.

-- 1) 케이스별 초기 실행 프로필.
--    worker 추출성공/최초확정 경로가 state 를 PENDING→RUNNING 으로 조건부 claim 하고,
--    execution_token 으로 stale-reaper 의 늦은 완료·실패 갱신을 fencing 한다.
--    OCR 선택값은 실행 주체(추출 워커)가 달라 application_case_extraction 에 스냅샷한다(여기 두지 않음).
CREATE TABLE IF NOT EXISTS application_case_initial_run (
    application_case_id       BIGINT NOT NULL,
    state                     VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/DONE/FAILED
    job_analysis_provider     VARCHAR(20) NULL,   -- LOCAL/CLAUDE/OPENAI (미선택=NULL → 현행 기본 체인)
    company_analysis_provider VARCHAR(20) NULL,   -- OPENAI/CLAUDE/LOCAL
    execution_token           CHAR(36) NULL,      -- RUNNING claim 시 UUID 발급(fencing 토큰)
    started_at                DATETIME NULL,
    finished_at               DATETIME NULL,
    failure_reason            VARCHAR(255) NULL,
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (application_case_id),                          -- 케이스 1:1(초기 실행은 케이스당 한 번)
    KEY idx_initial_run_stale (state, started_at),             -- stale-reaper: state='RUNNING' AND started_at < 임계
    CONSTRAINT chk_initial_run_state CHECK (state IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT fk_initial_run_case FOREIGN KEY (application_case_id)
        REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2) application_case_extraction: 선택 OCR provider 스냅샷.
--    실제 OCR provider·모델·시도 경로는 기존 model_versions_json, 폴백은 fallback_eligible/fallback_reason 재사용.
SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'application_case_extraction'
            AND column_name = 'ocr_requested_provider');
SET @ddl = IF(@c = 0,
    'ALTER TABLE application_case_extraction ADD COLUMN ocr_requested_provider VARCHAR(20) NULL AFTER source_type',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) job_analysis: 요청/실제 provider·모델 + 폴백 여부 + 시도 경로(JSON) + 실행 모드.
SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'requested_provider');
SET @ddl = IF(@c = 0,
    'ALTER TABLE job_analysis ADD COLUMN requested_provider VARCHAR(20) NULL AFTER admin_memo',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'actual_provider');
SET @ddl = IF(@c = 0,
    'ALTER TABLE job_analysis ADD COLUMN actual_provider VARCHAR(20) NULL AFTER requested_provider',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'actual_model');
SET @ddl = IF(@c = 0,
    'ALTER TABLE job_analysis ADD COLUMN actual_model VARCHAR(80) NULL AFTER actual_provider',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'fallback_used');
SET @ddl = IF(@c = 0,
    'ALTER TABLE job_analysis ADD COLUMN fallback_used TINYINT(1) NULL AFTER actual_model',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'attempt_path');
SET @ddl = IF(@c = 0,
    'ALTER TABLE job_analysis ADD COLUMN attempt_path JSON NULL AFTER fallback_used',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'job_analysis' AND column_name = 'run_mode');
SET @ddl = IF(@c = 0,
    'ALTER TABLE job_analysis ADD COLUMN run_mode VARCHAR(20) NULL AFTER attempt_path',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) company_analysis: 동일한 6컬럼.
SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'requested_provider');
SET @ddl = IF(@c = 0,
    'ALTER TABLE company_analysis ADD COLUMN requested_provider VARCHAR(20) NULL AFTER admin_memo',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'actual_provider');
SET @ddl = IF(@c = 0,
    'ALTER TABLE company_analysis ADD COLUMN actual_provider VARCHAR(20) NULL AFTER requested_provider',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'actual_model');
SET @ddl = IF(@c = 0,
    'ALTER TABLE company_analysis ADD COLUMN actual_model VARCHAR(80) NULL AFTER actual_provider',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'fallback_used');
SET @ddl = IF(@c = 0,
    'ALTER TABLE company_analysis ADD COLUMN fallback_used TINYINT(1) NULL AFTER actual_model',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'attempt_path');
SET @ddl = IF(@c = 0,
    'ALTER TABLE company_analysis ADD COLUMN attempt_path JSON NULL AFTER fallback_used',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'company_analysis' AND column_name = 'run_mode');
SET @ddl = IF(@c = 0,
    'ALTER TABLE company_analysis ADD COLUMN run_mode VARCHAR(20) NULL AFTER attempt_path',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
