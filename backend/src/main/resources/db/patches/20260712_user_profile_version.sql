-- 프로필 저장 시점 스냅샷과 A 영역 AI 분석의 입력 버전 연결을 추가한다.
-- 기존 프로필은 v1(MIGRATION)로 한 번만 백필하며 재실행해도 중복 행을 만들지 않는다.

SET @ct_profile_version_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_profile'
       AND COLUMN_NAME = 'version_no'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_version_column_exists = 0,
    'ALTER TABLE user_profile ADD COLUMN version_no INT NOT NULL DEFAULT 1 COMMENT ''사용자별 현재 프로필 스냅샷 버전'' AFTER preferences',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_resume_detail_deleted_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_resume_detail'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_profile_version_ddl := IF(
    @ct_resume_detail_deleted_column_exists = 0,
    'ALTER TABLE user_resume_detail ADD COLUMN deleted_at DATETIME NULL COMMENT ''회원 탈퇴 개인정보 삭제 시각'' AFTER desired_condition_json',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_profile_deleted_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_profile'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_deleted_column_exists = 0,
    'ALTER TABLE user_profile ADD COLUMN deleted_at DATETIME NULL COMMENT ''회원 탈퇴 개인정보 삭제 시각'' AFTER version_no',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_resume_detail_deleted_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_resume_detail'
       AND INDEX_NAME = 'idx_user_resume_detail_deleted'
);
SET @ct_profile_version_ddl := IF(
    @ct_resume_detail_deleted_index_exists = 0,
    'ALTER TABLE user_resume_detail ADD INDEX idx_user_resume_detail_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

CREATE TABLE IF NOT EXISTS user_profile_version (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    version_no       INT NOT NULL,
    desired_job      VARCHAR(255) NULL,
    desired_industry VARCHAR(255) NULL,
    education        JSON NULL,
    career           JSON NULL,
    projects         JSON NULL,
    skills           JSON NULL,
    certificates     JSON NULL,
    languages        JSON NULL,
    portfolio_links  JSON NULL,
    resume_text      MEDIUMTEXT NULL,
    self_intro       MEDIUMTEXT NULL,
    preferences      JSON NULL,
    source           VARCHAR(40) NOT NULL,
    deleted_at       DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_version (user_id, version_no),
    KEY idx_user_profile_version_created (user_id, created_at DESC),
    KEY idx_user_profile_version_deleted (user_id, deleted_at),
    CONSTRAINT fk_user_profile_version_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '분석 재현용 사용자 프로필 불변 스냅샷';

-- 구버전 서비스는 빈 프로필을 DB에 만들지 않고도 규칙 기반 분석을 저장할 수 있었다.
INSERT IGNORE INTO user_profile (user_id, version_no)
SELECT DISTINCT user_id, 1
  FROM profile_ai_analysis;

INSERT IGNORE INTO user_profile_version (
    user_id, version_no, desired_job, desired_industry, education, career, projects,
    skills, certificates, languages, portfolio_links, resume_text, self_intro,
    preferences, source, created_at
)
SELECT user_id, version_no, desired_job, desired_industry, education, career, projects,
       skills, certificates, languages, portfolio_links, resume_text, self_intro,
       preferences, 'MIGRATION', updated_at
  FROM user_profile;

SET @ct_profile_ai_version_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'profile_ai_analysis'
       AND COLUMN_NAME = 'profile_version_id'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_ai_version_column_exists = 0,
    'ALTER TABLE profile_ai_analysis ADD COLUMN profile_version_id BIGINT NULL COMMENT ''분석에 사용한 user_profile_version.id'' AFTER user_id',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_profile_ai_deleted_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'profile_ai_analysis'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_ai_deleted_column_exists = 0,
    'ALTER TABLE profile_ai_analysis ADD COLUMN deleted_at DATETIME NULL COMMENT ''회원 탈퇴 개인정보 삭제 시각'' AFTER status',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

-- 이미 탈퇴한 계정도 새 개인정보 삭제 계약과 동일하게 프로필 원문·구조화 값·AI 산출물을 지운다.
UPDATE user_profile profile
JOIN users account ON account.id = profile.user_id
   SET profile.desired_job = NULL,
       profile.desired_industry = NULL,
       profile.education = NULL,
       profile.career = NULL,
       profile.projects = NULL,
       profile.skills = NULL,
       profile.certificates = NULL,
       profile.languages = NULL,
       profile.portfolio_links = NULL,
       profile.resume_text = NULL,
       profile.self_intro = NULL,
       profile.preferences = NULL,
       profile.deleted_at = COALESCE(profile.deleted_at, NOW()),
       profile.updated_at = NOW()
 WHERE account.status = 'DELETED';

UPDATE user_profile_version version
JOIN users account ON account.id = version.user_id
   SET version.desired_job = NULL,
       version.desired_industry = NULL,
       version.education = NULL,
       version.career = NULL,
       version.projects = NULL,
       version.skills = NULL,
       version.certificates = NULL,
       version.languages = NULL,
       version.portfolio_links = NULL,
       version.resume_text = NULL,
       version.self_intro = NULL,
       version.preferences = NULL,
       version.deleted_at = COALESCE(version.deleted_at, NOW())
 WHERE account.status = 'DELETED';

UPDATE profile_ai_analysis analysis
JOIN users account ON account.id = analysis.user_id
   SET analysis.summary = NULL,
       analysis.strengths = JSON_ARRAY(),
       analysis.gaps = JSON_ARRAY(),
       analysis.recommendations = JSON_ARRAY(),
       analysis.extracted_skills = JSON_ARRAY(),
       analysis.criteria = JSON_ARRAY(),
       analysis.job_family = NULL,
       analysis.completeness_score = NULL,
       analysis.ai_score = NULL,
       analysis.quality_warnings = JSON_ARRAY(),
       analysis.status = 'DELETED',
       analysis.deleted_at = COALESCE(analysis.deleted_at, NOW()),
       analysis.updated_at = NOW()
 WHERE account.status = 'DELETED';

UPDATE user_resume_detail detail
JOIN users account ON account.id = detail.user_id
   SET detail.education_json = NULL,
       detail.career_json = NULL,
       detail.certificate_json = NULL,
       detail.language_json = NULL,
       detail.award_json = NULL,
       detail.activity_json = NULL,
       detail.skill_json = NULL,
       detail.portfolio_json = NULL,
       detail.desired_condition_json = NULL,
       detail.deleted_at = COALESCE(detail.deleted_at, NOW()),
       detail.updated_at = NOW()
 WHERE account.status = 'DELETED';

UPDATE profile_ai_analysis analysis
JOIN user_profile_version version
  ON version.user_id = analysis.user_id
 AND version.version_no = (
        SELECT MAX(latest.version_no)
          FROM user_profile_version latest
         WHERE latest.user_id = analysis.user_id
     )
   SET analysis.profile_version_id = version.id
 WHERE analysis.profile_version_id IS NULL;

SET @ct_profile_version_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'profile_ai_analysis'
       AND INDEX_NAME = 'idx_profile_ai_profile_version'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_version_index_exists = 0,
    'ALTER TABLE profile_ai_analysis ADD INDEX idx_profile_ai_profile_version (profile_version_id)',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_profile_deleted_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_profile'
       AND INDEX_NAME = 'idx_user_profile_deleted'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_deleted_index_exists = 0,
    'ALTER TABLE user_profile ADD INDEX idx_user_profile_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_profile_ai_deleted_index_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'profile_ai_analysis'
       AND INDEX_NAME = 'idx_profile_ai_deleted'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_ai_deleted_index_exists = 0,
    'ALTER TABLE profile_ai_analysis ADD INDEX idx_profile_ai_deleted (user_id, deleted_at)',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_profile_version_fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'profile_ai_analysis'
       AND CONSTRAINT_NAME = 'fk_profile_ai_profile_version'
       AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @ct_profile_version_ddl := IF(
    @ct_profile_version_fk_exists = 0,
    'ALTER TABLE profile_ai_analysis ADD CONSTRAINT fk_profile_ai_profile_version FOREIGN KEY (profile_version_id) REFERENCES user_profile_version (id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE ct_profile_version_stmt FROM @ct_profile_version_ddl;
EXECUTE ct_profile_version_stmt;
DEALLOCATE PREPARE ct_profile_version_stmt;

SET @ct_profile_version_invalid_current := (
    SELECT COUNT(*)
      FROM user_profile current
      LEFT JOIN user_profile_version version
        ON version.user_id = current.user_id
       AND version.version_no = current.version_no
     WHERE version.id IS NULL
);
SET @ct_profile_version_invalid_analysis := (
    SELECT COUNT(*)
      FROM profile_ai_analysis analysis
      LEFT JOIN user_profile_version version ON version.id = analysis.profile_version_id
     WHERE analysis.profile_version_id IS NULL
        OR version.id IS NULL
        OR version.user_id <> analysis.user_id
);
SET @ct_profile_version_verification_ok := IF(
       @ct_profile_version_invalid_current = 0
   AND @ct_profile_version_invalid_analysis = 0,
   1, 0
);

DROP TEMPORARY TABLE IF EXISTS ct_profile_version_guard;
CREATE TEMPORARY TABLE ct_profile_version_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_profile_version_guard (guard_ok) VALUES (@ct_profile_version_verification_ok);

SELECT @ct_profile_version_invalid_current AS current_without_snapshot,
       @ct_profile_version_invalid_analysis AS analysis_without_snapshot,
       IF(@ct_profile_version_verification_ok = 1, 'PASS', 'FAIL') AS result;

DROP TEMPORARY TABLE IF EXISTS ct_profile_version_guard;
