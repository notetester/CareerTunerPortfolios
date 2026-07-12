-- C 대시보드 사용자 할 일의 삭제를 물리 DELETE가 아닌 tombstone으로 보존한다.
-- 기존 행은 deleted_at=NULL로 활성 상태를 유지한다. 재실행 가능한 additive patch.

SET @ct_dashboard_todo_deleted_at_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'dashboard_todo'
          AND column_name = 'deleted_at'
    ),
    'SELECT 1',
    'ALTER TABLE dashboard_todo ADD COLUMN deleted_at DATETIME NULL AFTER completed_at'
);
PREPARE ct_dashboard_todo_deleted_at_stmt FROM @ct_dashboard_todo_deleted_at_sql;
EXECUTE ct_dashboard_todo_deleted_at_stmt;
DEALLOCATE PREPARE ct_dashboard_todo_deleted_at_stmt;

SET @ct_dashboard_todo_deleted_idx_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'dashboard_todo'
          AND index_name = 'idx_dashboard_todo_active'
    ),
    'SELECT 1',
    'ALTER TABLE dashboard_todo ADD INDEX idx_dashboard_todo_active (user_id, deleted_at, created_at)'
);
PREPARE ct_dashboard_todo_deleted_idx_stmt FROM @ct_dashboard_todo_deleted_idx_sql;
EXECUTE ct_dashboard_todo_deleted_idx_stmt;
DEALLOCATE PREPARE ct_dashboard_todo_deleted_idx_stmt;
