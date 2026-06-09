-- C 적합도 분석 완료 기준 보강.
-- 설명 가능한 분석 근거와 학습 체크리스트를 C 결과에 저장하기 위한 변경이다.
-- A 프로필, B 공고/지원 건, D 면접 원본 테이블은 수정하지 않는다.

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN source_snapshot JSON NULL AFTER strategy',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'source_snapshot'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN score_basis JSON NULL AFTER source_snapshot',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'score_basis'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN gap_recommendations JSON NULL AFTER score_basis',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'gap_recommendations'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN certificate_recommendations JSON NULL AFTER gap_recommendations',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'certificate_recommendations'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN strategy_actions JSON NULL AFTER certificate_recommendations',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'strategy_actions'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN model VARCHAR(80) NULL AFTER strategy_actions',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'model'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''SUCCESS'' AFTER model',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'status'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN error_message VARCHAR(1000) NULL AFTER status',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'error_message'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS fit_analysis_learning_task (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    fit_analysis_id     BIGINT NOT NULL,
    skill               VARCHAR(255) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    practice_task       VARCHAR(1000) NULL,
    expected_duration   VARCHAR(100) NULL,
    priority            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    sort_order          INT NOT NULL DEFAULT 0,
    completed           TINYINT(1) NOT NULL DEFAULT 0,
    completed_at        DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fit_learning_task_analysis (fit_analysis_id),
    CONSTRAINT fk_fit_learning_task_analysis FOREIGN KEY (fit_analysis_id) REFERENCES fit_analysis (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
