-- E 첨삭 입력 provenance와 soft-delete 수명주기를 추가한다. 재실행 가능한 additive patch.

SET @ct_e_correction_source_snapshot_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'correction_request'
          AND column_name = 'source_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE correction_request ADD COLUMN source_snapshot JSON NULL AFTER result_json'
);
PREPARE ct_e_correction_source_snapshot_stmt FROM @ct_e_correction_source_snapshot_sql;
EXECUTE ct_e_correction_source_snapshot_stmt;
DEALLOCATE PREPARE ct_e_correction_source_snapshot_stmt;

SET @ct_e_correction_deleted_at_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'correction_request'
          AND column_name = 'deleted_at'
    ),
    'SELECT 1',
    'ALTER TABLE correction_request ADD COLUMN deleted_at DATETIME NULL AFTER admin_memo'
);
PREPARE ct_e_correction_deleted_at_stmt FROM @ct_e_correction_deleted_at_sql;
EXECUTE ct_e_correction_deleted_at_stmt;
DEALLOCATE PREPARE ct_e_correction_deleted_at_stmt;

SET @ct_e_correction_active_idx_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'correction_request'
          AND index_name = 'idx_correction_request_active'
    ),
    'SELECT 1',
    'ALTER TABLE correction_request ADD INDEX idx_correction_request_active (user_id, deleted_at, created_at)'
);
PREPARE ct_e_correction_active_idx_stmt FROM @ct_e_correction_active_idx_sql;
EXECUTE ct_e_correction_active_idx_stmt;
DEALLOCATE PREPARE ct_e_correction_active_idx_stmt;

-- 이미 탈퇴한 계정의 첨삭 원문·개선문·모델 결과·운영 메모도 즉시 익명화한다.
UPDATE correction_request correction
INNER JOIN users account ON account.id = correction.user_id
   SET correction.original_text = '',
       correction.improved_text = NULL,
       correction.result_json = NULL,
       correction.source_snapshot = NULL,
       correction.admin_memo = NULL,
       correction.status = 'DELETED',
       correction.deleted_at = COALESCE(correction.deleted_at, NOW())
 WHERE account.status = 'DELETED'
    OR account.deleted_at IS NOT NULL;
