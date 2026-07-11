-- 관리자 화면에서 삭제 가능한 엔티티를 물리 삭제 대신 소프트 삭제로 통일한다.
-- legal_document_version과 legal_clause는 각각 삭제 시각만 기록해 법적 이력을 보존한다.
-- collaboration_conversation_ban과 admin_permission_group_item은 재등록 시 기존 행의
-- deleted_at을 NULL로 복원하며 UNIQUE 키와 감사 가능한 동일 row를 유지한다.
-- MySQL 8에서 재실행 가능한 information_schema guard를 사용한다.

-- notice
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notice'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE notice ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notice'
       AND INDEX_NAME = 'idx_notice_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE notice ADD INDEX idx_notice_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- faq
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'faq'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE faq ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'faq'
       AND INDEX_NAME = 'idx_faq_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE faq ADD INDEX idx_faq_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- community_guideline
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'community_guideline'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE community_guideline ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'community_guideline'
       AND INDEX_NAME = 'idx_guideline_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE community_guideline ADD INDEX idx_guideline_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- user_level_policy
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_level_policy'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE user_level_policy ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_level_policy'
       AND INDEX_NAME = 'idx_user_level_policy_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE user_level_policy ADD INDEX idx_user_level_policy_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- legal_document_version
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_document_version'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE legal_document_version ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_document_version'
       AND INDEX_NAME = 'idx_legal_ver_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE legal_document_version ADD INDEX idx_legal_ver_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- admin_fit_analysis_memo
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_fit_analysis_memo'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE admin_fit_analysis_memo ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_fit_analysis_memo'
       AND INDEX_NAME = 'idx_admin_fit_memo_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE admin_fit_analysis_memo ADD INDEX idx_admin_fit_memo_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- admin_career_run_memo
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_career_run_memo'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE admin_career_run_memo ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_career_run_memo'
       AND INDEX_NAME = 'idx_admin_career_memo_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE admin_career_run_memo ADD INDEX idx_admin_career_memo_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- chatbot_conversation_memory
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'chatbot_conversation_memory'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE chatbot_conversation_memory ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'chatbot_conversation_memory'
       AND INDEX_NAME = 'idx_ccm_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE chatbot_conversation_memory ADD INDEX idx_ccm_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- advertisement
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'advertisement'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE advertisement ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'advertisement'
       AND INDEX_NAME = 'idx_advertisement_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE advertisement ADD INDEX idx_advertisement_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- interview_knowledge
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_knowledge'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE interview_knowledge ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'interview_knowledge'
       AND INDEX_NAME = 'idx_interview_knowledge_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE interview_knowledge ADD INDEX idx_interview_knowledge_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- collaboration_conversation_ban
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'collaboration_conversation_ban'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE collaboration_conversation_ban ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'collaboration_conversation_ban'
       AND INDEX_NAME = 'idx_collab_conv_ban_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE collaboration_conversation_ban ADD INDEX idx_collab_conv_ban_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- admin_permission_group_item
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_permission_group_item'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE admin_permission_group_item ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_permission_group_item'
       AND INDEX_NAME = 'idx_admin_perm_group_item_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE admin_permission_group_item ADD INDEX idx_admin_perm_group_item_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- legal_clause
SET @ct_column_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_clause'
       AND COLUMN_NAME = 'deleted_at'
);
SET @ct_ddl := IF(
    @ct_column_exists = 0,
    'ALTER TABLE legal_clause ADD COLUMN deleted_at DATETIME NULL COMMENT ''관리자 소프트 삭제 시각''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_index_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_clause'
       AND INDEX_NAME = 'idx_legal_clause_deleted'
);
SET @ct_ddl := IF(
    @ct_index_exists = 0,
    'ALTER TABLE legal_clause ADD INDEX idx_legal_clause_deleted (deleted_at)',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- 법적 문서 버전을 실수로 물리 삭제해 조항이 연쇄 삭제되지 않도록 FK도 보존 우선으로 바꾼다.
-- 새 이름의 RESTRICT FK를 먼저 만든 뒤 기존 이름을 제거해 어느 시점에도 FK가 0개가 되지 않게 한다.
SET @ct_legal_restrict_fk_name_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_clause'
       AND CONSTRAINT_NAME = 'fk_legal_clause_ver_restrict'
);
SET @ct_legal_restrict_fk_valid := (
    SELECT IF(COUNT(*) = 1, 1, 0)
      FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS referential
      JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE key_column
        ON key_column.CONSTRAINT_SCHEMA = referential.CONSTRAINT_SCHEMA
       AND key_column.TABLE_NAME = referential.TABLE_NAME
       AND key_column.CONSTRAINT_NAME = referential.CONSTRAINT_NAME
     WHERE referential.CONSTRAINT_SCHEMA = DATABASE()
       AND referential.TABLE_NAME = 'legal_clause'
       AND referential.CONSTRAINT_NAME = 'fk_legal_clause_ver_restrict'
       AND referential.DELETE_RULE = 'RESTRICT'
       AND referential.REFERENCED_TABLE_NAME = 'legal_document_version'
       AND key_column.COLUMN_NAME = 'version_id'
       AND key_column.REFERENCED_COLUMN_NAME = 'id'
       AND key_column.ORDINAL_POSITION = 1
);

DROP TEMPORARY TABLE IF EXISTS ct_legal_restrict_fk_preflight_guard;
CREATE TEMPORARY TABLE ct_legal_restrict_fk_preflight_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_legal_restrict_fk_preflight_guard (guard_ok)
VALUES (IF(@ct_legal_restrict_fk_name_exists = 0 OR @ct_legal_restrict_fk_valid = 1, 1, 0));

SET @ct_ddl := IF(
    @ct_legal_restrict_fk_name_exists = 0,
    'ALTER TABLE legal_clause ADD CONSTRAINT fk_legal_clause_ver_restrict FOREIGN KEY (version_id) REFERENCES legal_document_version (id) ON DELETE RESTRICT',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

SET @ct_legal_restrict_fk_valid := (
    SELECT IF(COUNT(*) = 1, 1, 0)
      FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS referential
      JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE key_column
        ON key_column.CONSTRAINT_SCHEMA = referential.CONSTRAINT_SCHEMA
       AND key_column.TABLE_NAME = referential.TABLE_NAME
       AND key_column.CONSTRAINT_NAME = referential.CONSTRAINT_NAME
     WHERE referential.CONSTRAINT_SCHEMA = DATABASE()
       AND referential.TABLE_NAME = 'legal_clause'
       AND referential.CONSTRAINT_NAME = 'fk_legal_clause_ver_restrict'
       AND referential.DELETE_RULE = 'RESTRICT'
       AND referential.REFERENCED_TABLE_NAME = 'legal_document_version'
       AND key_column.COLUMN_NAME = 'version_id'
       AND key_column.REFERENCED_COLUMN_NAME = 'id'
       AND key_column.ORDINAL_POSITION = 1
);
SET @ct_legacy_legal_fk_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_clause'
       AND CONSTRAINT_NAME = 'fk_legal_clause_ver'
);
SET @ct_ddl := IF(
    @ct_legal_restrict_fk_valid = 1 AND @ct_legacy_legal_fk_exists = 1,
    'ALTER TABLE legal_clause DROP FOREIGN KEY fk_legal_clause_ver',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;
DROP TEMPORARY TABLE IF EXISTS ct_legal_restrict_fk_preflight_guard;

-- 삭제된 DRAFT 법적 문서가 같은 doc_type의 새 DRAFT 생성을 막지 않도록
-- generated unique slot은 활성(deleted_at IS NULL) DRAFT만 계산한다.
SET @ct_legal_draft_expression := (
    SELECT GENERATION_EXPRESSION
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_document_version'
       AND COLUMN_NAME = 'draft_doc_type'
);
SET @ct_legal_draft_expression_normalized := LOWER(
    REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(COALESCE(@ct_legal_draft_expression, ''), CHAR(96), ''),
                        CHAR(92), ''
                    ),
                    ' ', ''
                ),
                '(', ''
            ),
            ')', ''
        ),
        '_utf8mb4', ''
    )
);
SET @ct_ddl := IF(
    @ct_legal_draft_expression_normalized <>
        'casewhenstatus=''draft''anddeleted_atisnullthendoc_typeend',
    'ALTER TABLE legal_document_version MODIFY COLUMN draft_doc_type VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN status = ''DRAFT'' AND deleted_at IS NULL THEN doc_type END) VIRTUAL COMMENT ''활성 DRAFT 유일성 제약용 파생 컬럼''',
    'SELECT 1'
);
PREPARE ct_stmt FROM @ct_ddl;
EXECUTE ct_stmt;
DEALLOCATE PREPARE ct_stmt;

-- 13개 대상 컬럼/인덱스와 legal FK/generated expression을 함께 fail-closed 검증한다.
SET @ct_legal_restrict_fk_valid := (
    SELECT IF(COUNT(*) = 1, 1, 0)
      FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS referential
      JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE key_column
        ON key_column.CONSTRAINT_SCHEMA = referential.CONSTRAINT_SCHEMA
       AND key_column.TABLE_NAME = referential.TABLE_NAME
       AND key_column.CONSTRAINT_NAME = referential.CONSTRAINT_NAME
     WHERE referential.CONSTRAINT_SCHEMA = DATABASE()
       AND referential.TABLE_NAME = 'legal_clause'
       AND referential.CONSTRAINT_NAME = 'fk_legal_clause_ver_restrict'
       AND referential.DELETE_RULE = 'RESTRICT'
       AND referential.REFERENCED_TABLE_NAME = 'legal_document_version'
       AND key_column.COLUMN_NAME = 'version_id'
       AND key_column.REFERENCED_COLUMN_NAME = 'id'
       AND key_column.ORDINAL_POSITION = 1
);
SET @ct_legacy_legal_fk_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_clause'
       AND CONSTRAINT_NAME = 'fk_legal_clause_ver'
);
SET @ct_legal_draft_expression := (
    SELECT GENERATION_EXPRESSION
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_document_version'
       AND COLUMN_NAME = 'draft_doc_type'
);
SET @ct_legal_draft_expression_normalized := LOWER(
    REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(COALESCE(@ct_legal_draft_expression, ''), CHAR(96), ''),
                        CHAR(92), ''
                    ),
                    ' ', ''
                ),
                '(', ''
            ),
            ')', ''
        ),
        '_utf8mb4', ''
    )
);
SET @ct_legal_draft_expression_valid := IF(
    @ct_legal_draft_expression_normalized =
        'casewhenstatus=''draft''anddeleted_atisnullthendoc_typeend',
    1,
    0
);

DROP TEMPORARY TABLE IF EXISTS ct_admin_soft_delete_target;
CREATE TEMPORARY TABLE ct_admin_soft_delete_target (
    table_name VARCHAR(64) NOT NULL,
    index_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (table_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
INSERT INTO ct_admin_soft_delete_target (table_name, index_name)
VALUES
    ('notice', 'idx_notice_deleted'),
    ('faq', 'idx_faq_deleted'),
    ('community_guideline', 'idx_guideline_deleted'),
    ('user_level_policy', 'idx_user_level_policy_deleted'),
    ('legal_document_version', 'idx_legal_ver_deleted'),
    ('admin_fit_analysis_memo', 'idx_admin_fit_memo_deleted'),
    ('admin_career_run_memo', 'idx_admin_career_memo_deleted'),
    ('chatbot_conversation_memory', 'idx_ccm_deleted'),
    ('advertisement', 'idx_advertisement_deleted'),
    ('interview_knowledge', 'idx_interview_knowledge_deleted'),
    ('collaboration_conversation_ban', 'idx_collab_conv_ban_deleted'),
    ('admin_permission_group_item', 'idx_admin_perm_group_item_deleted'),
    ('legal_clause', 'idx_legal_clause_deleted');

SET @ct_admin_soft_delete_target_count := (
    SELECT COUNT(*) FROM ct_admin_soft_delete_target
);
SET @ct_admin_soft_delete_column_count := (
    SELECT COUNT(*)
      FROM ct_admin_soft_delete_target target
      JOIN INFORMATION_SCHEMA.COLUMNS column_info
        ON column_info.TABLE_SCHEMA = DATABASE()
       AND column_info.TABLE_NAME = target.table_name
       AND column_info.COLUMN_NAME = 'deleted_at'
);
SET @ct_admin_soft_delete_index_count := (
    SELECT COUNT(*)
      FROM ct_admin_soft_delete_target target
      JOIN INFORMATION_SCHEMA.STATISTICS index_info
        ON index_info.TABLE_SCHEMA = DATABASE()
       AND index_info.TABLE_NAME = target.table_name
       AND index_info.INDEX_NAME = target.index_name
       AND index_info.SEQ_IN_INDEX = 1
);
SET @ct_admin_soft_delete_verification_ok := IF(
       @ct_admin_soft_delete_target_count = 13
   AND @ct_admin_soft_delete_column_count = 13
   AND @ct_admin_soft_delete_index_count = 13
   AND @ct_legal_restrict_fk_valid = 1
   AND @ct_legacy_legal_fk_exists = 0
   AND @ct_legal_draft_expression_valid = 1,
    1,
    0
);

DROP TEMPORARY TABLE IF EXISTS ct_admin_soft_delete_verification_guard;
CREATE TEMPORARY TABLE ct_admin_soft_delete_verification_guard (
    guard_ok TINYINT NOT NULL CHECK (guard_ok = 1)
);
INSERT INTO ct_admin_soft_delete_verification_guard (guard_ok)
VALUES (@ct_admin_soft_delete_verification_ok);

SELECT target.table_name,
       IF(column_info.COLUMN_NAME IS NULL, 0, 1) AS has_deleted_at,
       IF(index_info.INDEX_NAME IS NULL, 0, 1) AS has_deleted_index
  FROM ct_admin_soft_delete_target target
  LEFT JOIN INFORMATION_SCHEMA.COLUMNS column_info
    ON column_info.TABLE_SCHEMA = DATABASE()
   AND column_info.TABLE_NAME = target.table_name
   AND column_info.COLUMN_NAME = 'deleted_at'
  LEFT JOIN INFORMATION_SCHEMA.STATISTICS index_info
    ON index_info.TABLE_SCHEMA = DATABASE()
   AND index_info.TABLE_NAME = target.table_name
   AND index_info.INDEX_NAME = target.index_name
   AND index_info.SEQ_IN_INDEX = 1
 ORDER BY target.table_name;

SELECT @ct_admin_soft_delete_column_count AS deleted_at_column_count,
       @ct_admin_soft_delete_index_count AS deleted_at_index_count,
       @ct_legal_restrict_fk_valid AS legal_restrict_fk_valid,
       @ct_legacy_legal_fk_exists AS legacy_legal_fk_exists,
       @ct_legal_draft_expression_valid AS legal_draft_expression_valid,
       IF(@ct_admin_soft_delete_verification_ok = 1, 'PASS', 'FAIL') AS result;

DROP TEMPORARY TABLE IF EXISTS ct_admin_soft_delete_verification_guard;
DROP TEMPORARY TABLE IF EXISTS ct_admin_soft_delete_target;
