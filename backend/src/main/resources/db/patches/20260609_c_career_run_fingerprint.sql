-- =====================================================================
--  C career_analysis_run 캐시 키 컬럼 추가 (input_fingerprint)
--  목적: 홈/대시보드/취업 분석 GET 조회마다 요약 AI를 재실행하던 동작을 제거하고,
--        입력이 동일하면(=데이터 미변경) 저장된 요약을 그대로 재사용하기 위함.
--  대상 테이블 career_analysis_run 은 C 소유. 컬럼만 추가하며 데이터 변환은 없다.
--  기존 행은 NULL 로 남고, 다음 조회 시 1회 재생성되며 자동으로 fingerprint 가 채워진다.
-- =====================================================================

-- 컬럼이 없을 때만 추가(재실행 안전).
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'career_analysis_run'
      AND COLUMN_NAME = 'input_fingerprint');

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE career_analysis_run ADD COLUMN input_fingerprint VARCHAR(64) NULL AFTER input_snapshot',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
