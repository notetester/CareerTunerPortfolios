-- E 리워드 이코노미: 사용자 활동 기반 포인트/레벨/쿠폰 적립 시스템.
-- 기존 크레딧 원장(credit_transaction, users.credit)에 얹는 earn 측 시스템.
-- 활동 포인트(activity_point)는 레벨 산정용 누적 XP, credit 은 사용 통화(AI 토큰).
-- 적립 크레딧은 credit_transaction 에 type='REWARD' 로 기록되어 기존 잔액/원장과 정합.

-- ---------------------------------------------------------------------
-- users 확장: 누적 활동 포인트 + 현재 레벨 (MySQL 8 안전 가드)
-- ---------------------------------------------------------------------
SET @has_activity_point := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'activity_point');
SET @sql := IF(@has_activity_point = 0,
    'ALTER TABLE users ADD COLUMN activity_point INT NOT NULL DEFAULT 0 COMMENT ''누적 활동 포인트(레벨 산정용 XP)'' AFTER credit',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_user_level := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'user_level');
SET @sql := IF(@has_user_level = 0,
    'ALTER TABLE users ADD COLUMN user_level INT NOT NULL DEFAULT 1 COMMENT ''현재 활동 레벨(user_level_policy 참조)'' AFTER activity_point',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- reward_rule: 활동 이벤트별 적립 규칙 (관리자 on/off + 값 조정)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reward_rule (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_code    VARCHAR(40)  NOT NULL COMMENT '적립 트리거. COMMUNITY_POST_CREATE/COMMUNITY_COMMENT_CREATE/APPLICATION_CASE_READY/DAILY_LOGIN/CREDIT_PURCHASE',
    name          VARCHAR(100) NOT NULL COMMENT '규칙 표시명',
    point_amount  INT          NOT NULL DEFAULT 0 COMMENT '지급 활동 포인트',
    credit_amount INT          NOT NULL DEFAULT 0 COMMENT '지급 크레딧(AI 토큰)',
    daily_cap     INT          NULL COMMENT '1일 지급 횟수 상한(NULL=무제한)',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0=미적립, 1=적립',
    description   VARCHAR(255) NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    updated_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_rule_event (event_code),
    KEY idx_reward_rule_enabled_sort (enabled, sort_order),
    KEY idx_reward_rule_updated_by (updated_by),
    CONSTRAINT fk_reward_rule_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '활동 이벤트별 리워드 적립 규칙';

-- ---------------------------------------------------------------------
-- user_level_policy: 레벨 임계 + 레벨업 보상
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_level_policy (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    level               INT          NOT NULL COMMENT '레벨 번호(1부터)',
    level_name          VARCHAR(50)  NOT NULL COMMENT '레벨 표시명',
    min_point           INT          NOT NULL COMMENT '이 레벨 도달에 필요한 누적 활동 포인트',
    levelup_credit      INT          NOT NULL DEFAULT 0 COMMENT '레벨업 시 지급 크레딧',
    levelup_coupon_code VARCHAR(50)  NULL COMMENT '레벨업 시 발급 쿠폰 코드(NULL=없음)',
    benefit_note        VARCHAR(255) NULL COMMENT '레벨 혜택 설명',
    active              TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_level_policy_level (level),
    KEY idx_user_level_policy_min_point (min_point)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '활동 레벨 임계 및 레벨업 보상 정책';

-- ---------------------------------------------------------------------
-- user_reward_history: 적립/레벨업/쿠폰 지급 감사
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_reward_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    event_code   VARCHAR(40)  NOT NULL COMMENT '적립 이벤트 또는 LEVEL_UP/COUPON_ISSUE',
    point_delta  INT          NOT NULL DEFAULT 0,
    credit_delta INT          NOT NULL DEFAULT 0,
    level_before INT          NULL,
    level_after  INT          NULL,
    ref_type     VARCHAR(40)  NULL COMMENT '연관 엔티티. POST/COMMENT/APPLICATION_CASE/PAYMENT/LEVEL',
    ref_id       BIGINT       NULL,
    reason       VARCHAR(255) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_reward_history_user (user_id, created_at),
    KEY idx_user_reward_history_event (event_code),
    CONSTRAINT fk_user_reward_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '사용자 리워드 적립/레벨업 이력';

-- ---------------------------------------------------------------------
-- coupon: 쿠폰 정의(할인/크레딧)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS coupon (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(50)  NOT NULL COMMENT '쿠폰 코드(대문자)',
    name           VARCHAR(100) NOT NULL,
    discount_type  VARCHAR(20)  NOT NULL COMMENT 'CREDIT=크레딧지급 / PERCENT=결제%할인 / AMOUNT=정액할인(원)',
    discount_value INT          NOT NULL DEFAULT 0 COMMENT 'PERCENT면 %, AMOUNT면 원, CREDIT이면 크레딧 수',
    min_purchase   INT          NOT NULL DEFAULT 0 COMMENT '최소 결제 금액(원). 할인형에 적용',
    valid_from     DATETIME     NULL,
    valid_until    DATETIME     NULL,
    max_issue      INT          NULL COMMENT '총 발급 상한(NULL=무제한)',
    issued_count   INT          NOT NULL DEFAULT 0,
    enabled        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_code (code),
    KEY idx_coupon_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '쿠폰 정의';

-- ---------------------------------------------------------------------
-- user_coupon: 사용자에게 발급된 쿠폰
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_coupon (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    code       VARCHAR(50) NOT NULL COMMENT '발급 시점 코드 스냅샷',
    status     VARCHAR(20) NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED/USED/EXPIRED',
    issued_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at    DATETIME    NULL,
    order_ref  BIGINT      NULL COMMENT '사용된 결제/충전 참조',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_coupon_user_status (user_id, status),
    KEY idx_user_coupon_coupon (coupon_id),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '사용자 발급 쿠폰';

-- ---------------------------------------------------------------------
-- 시드: 적립 규칙 / 레벨 정책 / 대표 쿠폰
-- ---------------------------------------------------------------------
INSERT IGNORE INTO reward_rule (event_code, name, point_amount, credit_amount, daily_cap, enabled, description, sort_order) VALUES
    ('COMMUNITY_POST_CREATE',    '커뮤니티 글 작성',   10, 0,    5, 1, '커뮤니티 게시글 작성 시 활동 포인트 적립(1일 5회)', 1),
    ('COMMUNITY_COMMENT_CREATE', '커뮤니티 댓글 작성',  3, 0,   10, 1, '댓글 작성 시 활동 포인트 적립(1일 10회)',          2),
    ('APPLICATION_CASE_READY',   '지원 건 분석 완료',  30, 1, NULL, 1, '지원 건 자동 분석 완료 시 포인트+크레딧 적립',      3),
    ('DAILY_LOGIN',              '일일 첫 로그인',      5, 0,    1, 1, '하루 첫 로그인 시 활동 포인트 적립(1일 1회)',        4),
    ('CREDIT_PURCHASE',          '크레딧 구매 페이백', 50, 0, NULL, 1, '크레딧 구매 확정 시 활동 포인트 페이백',            5);

INSERT IGNORE INTO user_level_policy (level, level_name, min_point, levelup_credit, levelup_coupon_code, benefit_note, active) VALUES
    (1, '새싹',   0,     0, NULL,              '기본 레벨',                       1),
    (2, '성장',   100,   5, NULL,              '레벨업 크레딧 5 지급',            1),
    (3, '숙련',   300,  10, NULL,              '레벨업 크레딧 10 지급',           1),
    (4, '전문가', 700,  20, 'LEVELUP_PRO',     '크레딧 20 + 전문가 쿠폰 지급',     1),
    (5, '마스터', 1500, 50, 'LEVELUP_MASTER',  '크레딧 50 + 마스터 쿠폰 지급',     1);

INSERT IGNORE INTO coupon (code, name, discount_type, discount_value, min_purchase, max_issue, enabled) VALUES
    ('WELCOME10',       '가입 환영 10% 할인',   'PERCENT', 10, 0,    NULL, 1),
    ('LEVELUP_PRO',     '전문가 달성 크레딧 20', 'CREDIT',  20, 0,    NULL, 1),
    ('LEVELUP_MASTER',  '마스터 달성 크레딧 50', 'CREDIT',  50, 0,    NULL, 1);
