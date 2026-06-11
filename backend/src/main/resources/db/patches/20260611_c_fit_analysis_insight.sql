-- C 적합도 분석 고도화.
-- 요구조건-스펙 비교 매트릭스, 분석 신뢰도, 지원 판단 카드를 fit_analysis(C 소유)에 저장한다.
-- A 프로필, B 공고/지원 건, D 면접 원본 테이블은 수정하지 않는다.

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN condition_matrix JSON NULL AFTER strategy_actions',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'condition_matrix'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN analysis_confidence JSON NULL AFTER condition_matrix',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'analysis_confidence'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN apply_decision JSON NULL AFTER analysis_confidence',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'apply_decision'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
