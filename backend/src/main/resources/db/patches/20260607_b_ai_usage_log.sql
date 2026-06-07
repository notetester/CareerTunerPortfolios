-- B 완성형 구현용 ai_usage_log 보강 컬럼.
-- 이미 생성된 개발 DB에는 schema.sql 변경이 자동 반영되지 않을 수 있으므로 1회 적용한다.
-- 컬럼이나 인덱스가 이미 있으면 각 ALTER/CREATE 문은 실패할 수 있다.

ALTER TABLE ai_usage_log
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' AFTER feature_type,
    ADD COLUMN model VARCHAR(80) NULL AFTER status,
    ADD COLUMN input_tokens INT NULL AFTER model,
    ADD COLUMN output_tokens INT NULL AFTER input_tokens,
    ADD COLUMN error_message VARCHAR(1000) NULL AFTER credit_used;

CREATE INDEX idx_ai_usage_feature ON ai_usage_log (feature_type);
