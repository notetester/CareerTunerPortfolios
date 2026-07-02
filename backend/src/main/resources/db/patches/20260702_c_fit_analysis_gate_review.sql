-- C 소유 gate review workflow: 운영자가 REVIEW_REQUIRED gate 결정을 처리(검토완료/재분석요청)하는 상태 컬럼.
-- fit_analysis_gate_result 는 C-only 테이블(#174)이라 additive ALTER 만 한다. schema.sql 캐노니컬에도 동일 반영.

SET @gate_review_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'fit_analysis_gate_result' AND column_name = 'review_status'
    ),
    'SELECT 1',
    'ALTER TABLE fit_analysis_gate_result
        ADD COLUMN review_status VARCHAR(30) NOT NULL DEFAULT ''PENDING'' AFTER rewrite_applied,
        ADD COLUMN reviewed_by BIGINT NULL AFTER review_status,
        ADD COLUMN reviewed_at DATETIME NULL AFTER reviewed_by,
        ADD KEY idx_fit_gate_result_review (review_status, created_at)'
);
PREPARE gate_review_stmt FROM @gate_review_sql;
EXECUTE gate_review_stmt;
DEALLOCATE PREPARE gate_review_stmt;
