-- AI 기능별 사용량 기반 크레딧 범위.
-- 실행 전에는 최소값을 고지하고, 성공 후 실제 token_usage를 credit_unit_tokens로 나눈 값을
-- [min_credit_cost, max_credit_cost] 범위로 제한해 정산한다.

SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_feature_benefit_policy ADD COLUMN min_credit_cost INT NOT NULL DEFAULT 0 AFTER default_credit_cost',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_feature_benefit_policy' AND column_name = 'min_credit_cost');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_feature_benefit_policy ADD COLUMN max_credit_cost INT NOT NULL DEFAULT 0 AFTER min_credit_cost',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_feature_benefit_policy' AND column_name = 'max_credit_cost');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_feature_benefit_policy ADD COLUMN credit_unit_tokens INT NOT NULL DEFAULT 1000 AFTER max_credit_cost',
    'SELECT 1') FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_feature_benefit_policy' AND column_name = 'credit_unit_tokens');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE ai_feature_benefit_policy
   SET min_credit_cost = default_credit_cost,
       max_credit_cost = default_credit_cost
 WHERE min_credit_cost = 0 AND max_credit_cost = 0 AND default_credit_cost > 0;

INSERT INTO ai_feature_benefit_policy
    (feature_type, benefit_code, charge_unit, included_in_ticket, default_credit_cost,
     min_credit_cost, max_credit_cost, credit_unit_tokens, active)
VALUES
    ('JOB_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 5, 1000, 1),
    ('COMPANY_RESEARCH', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 6, 1000, 1),
    ('JOB_POSTING_METADATA', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 3, 3, 6, 1000, 1),
    ('JOB_POSTING_OCR', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 12, 2000, 1),
    ('FIT_ANALYSIS', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 1, 1, 7, 1000, 1),
    ('DASHBOARD_SUMMARY', 'CAREER_STRATEGY', 'PER_REQUEST', 0, 1, 1, 2, 1000, 1),
    ('CAREER_TREND', 'CAREER_STRATEGY', 'PER_REQUEST', 0, 1, 1, 6, 1000, 1),
    ('LONG_TERM_ANALYSIS', 'CAREER_STRATEGY', 'PER_REQUEST', 0, 2, 2, 4, 1000, 1),
    ('INTERVIEW_QUESTION_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 4, 1000, 1),
    ('INTERVIEW_FOLLOWUP_GEN', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 2, 1000, 1),
    ('INTERVIEW_ANSWER_EVAL', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 4, 1000, 1),
    ('INTERVIEW_CRITIC', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 2, 1000, 1),
    ('INTERVIEW_MODEL_ANSWER', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 5, 1000, 1),
    ('INTERVIEW_REPORT', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 1, 1, 3, 1000, 1),
    ('INTERVIEW_VOICE_SCORING', 'VOICE_INTERVIEW', 'PER_SESSION', 1, 2, 2, 3, 1000, 1),
    ('CORRECTION_INTERVIEW_ANSWER', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('CORRECTION_SELF_INTRO', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('CORRECTION_RESUME', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('CORRECTION_PORTFOLIO', 'CORRECTION', 'PER_REQUEST', 0, 2, 2, 5, 1000, 1),
    ('PROFILE_COMPLETENESS', 'PROFILE_AI', 'PER_REQUEST', 0, 0, 0, 6, 1000, 1),
    ('PROFILE_SUMMARY', 'PROFILE_AI', 'PER_REQUEST', 0, 0, 0, 2, 1000, 1),
    ('PROFILE_SKILL_EXTRACT', 'PROFILE_AI', 'PER_REQUEST', 0, 0, 0, 0, 1000, 1)
ON DUPLICATE KEY UPDATE
    benefit_code = VALUES(benefit_code),
    charge_unit = VALUES(charge_unit),
    included_in_ticket = VALUES(included_in_ticket),
    default_credit_cost = VALUES(default_credit_cost),
    min_credit_cost = VALUES(min_credit_cost),
    max_credit_cost = VALUES(max_credit_cost),
    credit_unit_tokens = VALUES(credit_unit_tokens),
    active = VALUES(active);
