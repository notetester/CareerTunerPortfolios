-- E billing policy recovery: count-based benefits, AI feature policy, and purchasable benefit packs.

CREATE TABLE IF NOT EXISTS credit_product (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    code          VARCHAR(50) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    price         INT NOT NULL,
    credit_amount INT NOT NULL,
    description   VARCHAR(500) NULL,
    badge         VARCHAR(50) NULL,
    enabled       TINYINT(1) NOT NULL DEFAULT 1,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_credit_product_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS benefit_catalog (
    code        VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    active      TINYINT(1) NOT NULL DEFAULT 1,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS billing_product (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    code         VARCHAR(50) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    name         VARCHAR(100) NOT NULL,
    price        INT NOT NULL,
    description  VARCHAR(500) NULL,
    badge        VARCHAR(50) NULL,
    active       TINYINT(1) NOT NULL DEFAULT 1,
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_product_code (code),
    KEY idx_billing_product_type (product_type, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS billing_product_benefit (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    product_code  VARCHAR(50) NOT NULL,
    benefit_code  VARCHAR(50) NOT NULL,
    quantity      INT NOT NULL,
    validity_days INT NOT NULL DEFAULT 30,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_product_benefit (product_code, benefit_code),
    KEY idx_billing_product_benefit_code (benefit_code),
    CONSTRAINT fk_billing_product_benefit_product FOREIGN KEY (product_code) REFERENCES billing_product (code) ON DELETE CASCADE,
    CONSTRAINT fk_billing_product_benefit_catalog FOREIGN KEY (benefit_code) REFERENCES benefit_catalog (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

SET @add_balance_source_type = (
    SELECT IF(
      NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'user_benefit_balance'
           AND COLUMN_NAME = 'source_type'
      ),
      'ALTER TABLE user_benefit_balance ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT ''PLAN'' AFTER source_plan_code',
      'SELECT 1'
    )
);
PREPARE stmt FROM @add_balance_source_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_balance_source_code = (
    SELECT IF(
      NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'user_benefit_balance'
           AND COLUMN_NAME = 'source_code'
      ),
      'ALTER TABLE user_benefit_balance ADD COLUMN source_code VARCHAR(50) NULL AFTER source_type',
      'SELECT 1'
    )
);
PREPARE stmt FROM @add_balance_source_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE ai_feature_benefit_policy MODIFY feature_type VARCHAR(80) NOT NULL;
ALTER TABLE ai_usage_log MODIFY feature_type VARCHAR(80) NOT NULL;
ALTER TABLE credit_transaction MODIFY feature_type VARCHAR(80) NULL;
ALTER TABLE user_benefit_balance MODIFY source_plan_code VARCHAR(30) NULL;

INSERT INTO credit_product
    (code, name, price, credit_amount, description, badge, enabled, sort_order)
VALUES
    ('CREDIT_10', '크레딧 10개', 4900, 10, 'AI 기능 추가 이용용 크레딧', NULL, 1, 10),
    ('CREDIT_30', '크레딧 30개', 12900, 30, '자주 쓰는 사용자를 위한 크레딧 묶음', '인기', 1, 20),
    ('CREDIT_100', '크레딧 100개', 39000, 100, '팀 프로젝트와 시연용 대용량 크레딧', '최대 할인', 1, 30)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    credit_amount = VALUES(credit_amount),
    description = VALUES(description),
    badge = VALUES(badge),
    enabled = VALUES(enabled),
    sort_order = VALUES(sort_order);

INSERT INTO benefit_catalog
    (code, name, description, active, sort_order)
VALUES
    ('APPLICATION_ANALYSIS', '지원건 분석권', '공고, 기업, 적합도 등 지원 건 기반 AI 분석 사용권', 1, 10),
    ('MOCK_INTERVIEW', '모의면접권', '질문 생성, 답변 평가, 리포트 등 텍스트 면접 사용권', 1, 20),
    ('VOICE_INTERVIEW', '음성면접권', '음성 면접과 음성 답변 채점 사용권', 1, 30),
    ('VIDEO_ANALYSIS', '영상분석권', '영상/비언어 면접 분석 사용권', 1, 40),
    ('AVATAR_INTERVIEW', '아바타면접권', '아바타 면접관 세션 사용권', 1, 50),
    ('CORRECTION', 'AI 첨삭권', '면접 답변, 자기소개서, 이력서, 포트폴리오 첨삭 사용권', 1, 60),
    ('PROFILE_AI', '프로필 AI권', '프로필 요약, 기술 추출, 완성도 진단 사용권', 1, 70),
    ('CAREER_STRATEGY', '커리어 전략권', '부족 역량, 로드맵, 장기 경향, 대시보드 인사이트 사용권', 1, 80),
    ('COMMUNITY_AI', '커뮤니티 AI권', '면접 후기 요약, 태그 추천, 실제 질문 추출 사용권', 1, 90)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    active = VALUES(active),
    sort_order = VALUES(sort_order);

INSERT INTO subscription_benefit_policy
    (plan_code, benefit_code, benefit_name, benefit_type, quantity, reset_cycle, overage_policy, credit_cost, active, sort_order)
VALUES
    ('FREE', 'CORRECTION', 'AI 첨삭권', 'TICKET', 1, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('FREE', 'PROFILE_AI', '프로필 AI권', 'TICKET', 3, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('FREE', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 2, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('FREE', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 5, 'MONTHLY', 'CREDIT', 1, 1, 90),
    ('BASIC', 'CORRECTION', 'AI 첨삭권', 'TICKET', 10, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('BASIC', 'PROFILE_AI', '프로필 AI권', 'TICKET', 20, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('BASIC', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 15, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('BASIC', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 30, 'MONTHLY', 'CREDIT', 1, 1, 90),
    ('PRO', 'CORRECTION', 'AI 첨삭권', 'TICKET', 30, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('PRO', 'PROFILE_AI', '프로필 AI권', 'TICKET', 60, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('PRO', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 60, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('PRO', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 100, 'MONTHLY', 'CREDIT', 1, 1, 90),
    ('PREMIUM', 'CORRECTION', 'AI 첨삭권', 'TICKET', 60, 'MONTHLY', 'CREDIT', 2, 1, 60),
    ('PREMIUM', 'PROFILE_AI', '프로필 AI권', 'TICKET', 150, 'MONTHLY', 'CREDIT', 1, 1, 70),
    ('PREMIUM', 'CAREER_STRATEGY', '커리어 전략권', 'TICKET', 100, 'MONTHLY', 'CREDIT', 2, 1, 80),
    ('PREMIUM', 'COMMUNITY_AI', '커뮤니티 AI권', 'TICKET', 200, 'MONTHLY', 'CREDIT', 1, 1, 90)
ON DUPLICATE KEY UPDATE
    benefit_name = VALUES(benefit_name),
    benefit_type = VALUES(benefit_type),
    quantity = VALUES(quantity),
    reset_cycle = VALUES(reset_cycle),
    overage_policy = VALUES(overage_policy),
    credit_cost = VALUES(credit_cost),
    active = VALUES(active),
    sort_order = VALUES(sort_order);

UPDATE subscription_benefit_policy
   SET overage_policy = 'CREDIT',
       credit_cost = CASE benefit_code
           WHEN 'APPLICATION_ANALYSIS' THEN 2
           WHEN 'MOCK_INTERVIEW' THEN 2
           WHEN 'VOICE_INTERVIEW' THEN 3
           WHEN 'VIDEO_ANALYSIS' THEN 5
           WHEN 'AVATAR_INTERVIEW' THEN 6
           ELSE credit_cost
       END
 WHERE quantity > 0
   AND benefit_code IN ('APPLICATION_ANALYSIS', 'MOCK_INTERVIEW', 'VOICE_INTERVIEW', 'VIDEO_ANALYSIS', 'AVATAR_INTERVIEW');

INSERT INTO ai_feature_benefit_policy
    (feature_type, benefit_code, charge_unit, included_in_ticket, default_credit_cost, active)
VALUES
    ('PROFILE_SUMMARY', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_SKILL_EXTRACT', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_SELF_INTRO_KEYWORD', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_CAREER_KEYWORD', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('PROFILE_COMPLETENESS', 'PROFILE_AI', 'PER_REQUEST', 1, 1, 1),
    ('JOB_POSTING_METADATA', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('JOB_REQUIRED_CONDITION', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('JOB_PREFERRED_CONDITION', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('JOB_DUTY_SUMMARY', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('INTERVIEW_POINT_EXTRACTION', 'APPLICATION_ANALYSIS', 'PER_CASE', 1, 2, 1),
    ('GAP_ROADMAP_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_CASE', 1, 2, 1),
    ('CERTIFICATION_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_CASE', 1, 2, 1),
    ('CAREER_TREND_ANALYSIS', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 2, 1),
    ('NEXT_APPLICATION_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 2, 1),
    ('DASHBOARD_INSIGHT', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 2, 1),
    ('INTERVIEW_DIALOGUE', 'MOCK_INTERVIEW', 'PER_SESSION', 1, 2, 1),
    ('INTERVIEW_VOICE_SCORING', 'VOICE_INTERVIEW', 'PER_SESSION', 1, 3, 1),
    ('CORRECTION_INTERVIEW_ANSWER', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('CORRECTION_SELF_INTRO', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('CORRECTION_RESUME', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('CORRECTION_PORTFOLIO', 'CORRECTION', 'PER_REQUEST', 1, 2, 1),
    ('USAGE_PLAN_RECOMMENDATION', 'CAREER_STRATEGY', 'PER_REQUEST', 1, 1, 1),
    ('COMMUNITY_INTERVIEW_SUMMARY', 'COMMUNITY_AI', 'PER_POST', 1, 1, 1),
    ('COMMUNITY_AUTO_TAGGING', 'COMMUNITY_AI', 'PER_POST', 1, 1, 1),
    ('COMMUNITY_INTERVIEW_QUESTION_EXTRACTION', 'COMMUNITY_AI', 'PER_POST', 1, 1, 1),
    ('COMMUNITY_POST_RECOMMENDATION', 'COMMUNITY_AI', 'PER_REQUEST', 1, 1, 1)
ON DUPLICATE KEY UPDATE
    benefit_code = VALUES(benefit_code),
    charge_unit = VALUES(charge_unit),
    included_in_ticket = VALUES(included_in_ticket),
    default_credit_cost = VALUES(default_credit_cost),
    active = VALUES(active);

UPDATE ai_feature_benefit_policy
   SET default_credit_cost = CASE benefit_code
       WHEN 'APPLICATION_ANALYSIS' THEN 2
       WHEN 'MOCK_INTERVIEW' THEN 2
       WHEN 'VOICE_INTERVIEW' THEN 3
       WHEN 'VIDEO_ANALYSIS' THEN 5
       WHEN 'AVATAR_INTERVIEW' THEN 6
       WHEN 'CORRECTION' THEN 2
       WHEN 'PROFILE_AI' THEN 1
       WHEN 'CAREER_STRATEGY' THEN 2
       WHEN 'COMMUNITY_AI' THEN 1
       ELSE default_credit_cost
   END
 WHERE active = 1;

INSERT INTO billing_product
    (code, product_type, name, price, description, badge, active, sort_order)
VALUES
    ('PACK_APPLICATION_20', 'BENEFIT_PACK', '지원건 분석권 20장', 9900, '구독 포함량 소진 후 추가 분석용 사용권', NULL, 1, 110),
    ('PACK_INTERVIEW_10', 'BENEFIT_PACK', '모의면접권 10장', 9900, '질문 생성·평가·리포트 추가 이용권', NULL, 1, 120),
    ('PACK_CORRECTION_10', 'BENEFIT_PACK', 'AI 첨삭권 10장', 7900, '자기소개서·답변·이력서 첨삭 추가 이용권', NULL, 1, 130),
    ('PACK_VOICE_5', 'BENEFIT_PACK', '음성면접권 5장', 9900, '음성 면접 추가 이용권', NULL, 1, 140),
    ('PACK_VIDEO_3', 'BENEFIT_PACK', '영상분석권 3장', 12900, '영상/비언어 분석 추가 이용권', NULL, 1, 150),
    ('PACK_AVATAR_3', 'BENEFIT_PACK', '아바타면접권 3장', 14900, '아바타 면접관 추가 이용권', NULL, 1, 160)
ON DUPLICATE KEY UPDATE
    product_type = VALUES(product_type),
    name = VALUES(name),
    price = VALUES(price),
    description = VALUES(description),
    badge = VALUES(badge),
    active = VALUES(active),
    sort_order = VALUES(sort_order);

INSERT INTO billing_product_benefit
    (product_code, benefit_code, quantity, validity_days, sort_order)
VALUES
    ('PACK_APPLICATION_20', 'APPLICATION_ANALYSIS', 20, 30, 10),
    ('PACK_INTERVIEW_10', 'MOCK_INTERVIEW', 10, 30, 10),
    ('PACK_CORRECTION_10', 'CORRECTION', 10, 30, 10),
    ('PACK_VOICE_5', 'VOICE_INTERVIEW', 5, 30, 10),
    ('PACK_VIDEO_3', 'VIDEO_ANALYSIS', 3, 30, 10),
    ('PACK_AVATAR_3', 'AVATAR_INTERVIEW', 3, 30, 10)
ON DUPLICATE KEY UPDATE
    quantity = VALUES(quantity),
    validity_days = VALUES(validity_days),
    sort_order = VALUES(sort_order);
