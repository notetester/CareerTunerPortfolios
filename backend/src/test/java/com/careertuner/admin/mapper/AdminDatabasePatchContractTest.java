package com.careertuner.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminDatabasePatchContractTest {

    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path DATA = Path.of("src/main/resources/db/data.sql");
    private static final Path ROLE_PATCH = Path.of(
            "src/main/resources/db/patches/20260711_admin_seed_role_reconciliation.sql");
    private static final Path ASSIGNMENT_PATCH = Path.of(
            "src/main/resources/db/patches/20260711_admin_active_assignment_unique.sql");

    @Test
    void developmentSeedHasOneExplicitSuperAdminAndGeneralUsersStayUsers() throws Exception {
        String data = Files.readString(DATA);

        assertThat(data)
                .contains("'admin@careertuner.dev'")
                .contains("'SUPER_ADMIN', 'ACTIVE'")
                .contains("'jiwon.kim@careertuner.dev'")
                .contains("'seoyeon.lee@careertuner.dev'")
                .contains("'minsu.park@careertuner.dev'")
                .contains("'pending@careertuner.dev'")
                .contains("운영 DB에는 절대 적용하지 않는다");
    }

    @Test
    void roleRepairIsScopedByExpectedIdAndEmailAndKeepsAudit() throws Exception {
        String patch = Files.readString(ROLE_PATCH);

        assertThat(patch)
                .contains("(1, 'admin',   'admin@careertuner.dev',       'USER')")
                .contains("(2, 'jiwon',   'jiwon.kim@careertuner.dev',   'USER')")
                .contains("ON e.expected_id = u.id")
                .contains("AND e.email = u.email")
                .contains("CHECK (guard_ok = 1)")
                .contains("@ct_seed_identity_match_count = 5")
                .contains("@ct_nonseed_active_superadmin_count >= 1")
                .contains("MySQL은 한 statement 안에서 같은 TEMPORARY TABLE을 여러 번 열 수 없으므로")
                .contains("AND seed.expected_id IS NULL")
                .contains("INSERT INTO user_role_change_history")
                .contains("INSERT INTO admin_permission_audit")
                .contains("INSERT INTO admin_action_log")
                .contains("SEED_ROLE_BASELINE_20260711")
                .contains("NONSEED_SUPERADMIN_BASELINE_20260711")
                .contains("승격 정당화 아님, 소유자 확인 전 변경 금지");
    }

    @Test
    void activeAssignmentUniquenessUsesGeneratedKeyInSchemaAndPatch() throws Exception {
        String schema = Files.readString(SCHEMA);
        String patch = Files.readString(ASSIGNMENT_PATCH);

        assertThat(schema)
                .contains("`active_assignment_key` tinyint GENERATED ALWAYS AS")
                .contains("`uk_admin_user_perm_active` (`user_id`,`permission_code`,`active_assignment_key`)")
                .contains("`uk_admin_user_group_active` (`user_id`,`group_code`,`active_assignment_key`)");
        assertThat(patch)
                .contains("MAX(id) AS keep_id")
                .contains("DUPLICATE_ACTIVE_PERMISSION_REVOKED")
                .contains("DUPLICATE_ACTIVE_GROUP_REVOKED")
                .contains("COALESCE(@ct_perm_index_non_unique, 1) = 0")
                .contains("COALESCE(@ct_group_index_non_unique, 1) = 0")
                .contains("ADD UNIQUE KEY uk_admin_user_perm_active (user_id, permission_code, active_assignment_key)")
                .contains("ADD UNIQUE KEY uk_admin_user_group_active (user_id, group_code, active_assignment_key)");
    }
}
