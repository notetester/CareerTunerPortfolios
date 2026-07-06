-- =====================================================================
--  W6 프로필/계정 확충 (2026-07-05b)
--  1) 복수 닉네임 프로필: 한 계정이 여러 표시용 닉네임 프로필 보유
--     (커뮤니티/채팅 작성 시 선택). 제재·신고·차단은 계정 단위 귀속 —
--     닉네임 프로필은 "표시 계층"일 뿐(개인 차단 masked_label 패턴과 정합).
--  2) 채팅방 전용 프로필: 특정 방에서 사용할 닉네임 프로필을 별도 매핑.
--     collaboration 스키마(collaboration_conversation_member)는 수정 금지라
--     별도 매핑 테이블로 둔다. 익명 참가는 nickname_profile_id NULL.
--  3) 회원정보 확충(이력서 스펙): user_profile 과 충돌 없는 신규 테이블에
--     사람인/잡코리아식 상세 스펙(수상/대외활동/희망 근무조건 등) 저장.
--  4) 계정 기능 확충: 전화번호(선택·UNIQUE), 로그인 아이디(문자열) 컬럼 추가.
--
--  ※ schema.sql 은 수정하지 않는다(공통 영역). 이 패치 파일에만 신규 DDL 을 둔다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 복수 닉네임 프로필
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_nickname_profile (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '소유 계정(제재/신고/차단 귀속 단위)',
    nickname       VARCHAR(30)  NOT NULL COMMENT '표시용 닉네임. 전역 UNIQUE',
    avatar_file_id BIGINT       NULL COMMENT '아바타 파일(file 도메인 참조, 선택)',
    bio            VARCHAR(200) NULL COMMENT '한 줄 소개',
    is_default     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '기본 프로필 여부(계정당 1개)',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/HIDDEN',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_nickname_profile_nickname (nickname),
    KEY idx_user_nickname_profile_user (user_id, is_default DESC, created_at),
    CONSTRAINT chk_user_nickname_profile_status CHECK (status IN ('ACTIVE', 'HIDDEN')),
    CONSTRAINT fk_user_nickname_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '복수 닉네임 프로필(표시 계층 — 제재/신고/차단은 계정 단위)';

-- ---------------------------------------------------------------------
-- 2) 채팅방 전용 프로필 매핑
--    conversation 안에서 어떤 닉네임 프로필로 참여하는지. NULL 이면 익명 참가.
--    collaboration_conversation_member 를 수정하지 않기 위한 별도 매핑 테이블.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation_member_profile (
    conversation_id     BIGINT   NOT NULL,
    user_id             BIGINT   NOT NULL,
    nickname_profile_id BIGINT   NULL COMMENT '이 방에서 사용할 닉네임 프로필. NULL 이면 익명 참가',
    anonymous           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '익명 참가 여부(nickname_profile_id NULL 과 동조)',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_conversation_member_profile_profile (nickname_profile_id),
    CONSTRAINT fk_conversation_member_profile_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_member_profile_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_member_profile_nickname FOREIGN KEY (nickname_profile_id) REFERENCES user_nickname_profile (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '채팅방 전용 닉네임 프로필 매핑(NULL=익명 참가)';

-- ---------------------------------------------------------------------
-- 3) 회원정보 확충(이력서 스펙)
--    user_profile.desired_job/skills 등과 충돌 없이 별도 확장 테이블로 둔다.
--    추천 매칭이 새 필드도 참조할 수 있게 필드만 제공(매칭 서비스는 수정하지 않는다).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_resume_detail (
    user_id            BIGINT   NOT NULL,
    education_json     JSON     NULL COMMENT '[{school, major, gpa, gpaScale, graduationStatus, startDate, endDate}]',
    career_json        JSON     NULL COMMENT '[{company, role, employmentType, startDate, endDate, description}]',
    certificate_json   JSON     NULL COMMENT '[{name, issuer, acquiredAt}]',
    language_json      JSON     NULL COMMENT '[{test, score, acquiredAt}]',
    award_json         JSON     NULL COMMENT '[{title, host, awardedAt, description}]',
    activity_json      JSON     NULL COMMENT '[{title, organization, role, startDate, endDate, description}]',
    skill_json         JSON     NULL COMMENT '["React","Spring",...] — user_profile.skills 와 별개 확장 스택',
    portfolio_json     JSON     NULL COMMENT '[{label, url}]',
    desired_condition_json JSON NULL COMMENT '{jobCategoryLarge, jobCategoryMedium, employmentType, region, salaryMin, salaryMax, remote}',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_resume_detail_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '이력서 상세 스펙(사람인/잡코리아식 — 분석 정확도용, user_profile 확장)';

-- ---------------------------------------------------------------------
-- 4) 계정 기능 확충 — 전화번호(선택·UNIQUE), 로그인 아이디(문자열, 선택·UNIQUE)
--    인증은 선택적(스텁). phone_verified 는 향후 SMS 인증 도입 대비 컬럼만 예약.
-- ---------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN login_id       VARCHAR(50)  NULL COMMENT '로그인 아이디(문자열, 선택 설정·전역 UNIQUE·설정 후 변경 불가 정책)' AFTER email,
    ADD COLUMN phone          VARCHAR(20)  NULL COMMENT '전화번호(선택, 전역 UNIQUE)' AFTER login_id,
    ADD COLUMN phone_verified TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '전화번호 인증 여부(인증은 선택적·스텁)' AFTER phone,
    ADD UNIQUE KEY uk_users_login_id (login_id),
    ADD UNIQUE KEY uk_users_phone (phone);
