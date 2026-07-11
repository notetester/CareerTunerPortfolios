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
    private static final Path CRUD_CATALOG_PATCH = Path.of(
            "src/main/resources/db/patches/20260711_admin_permission_crud_catalog.sql");
    private static final Path SOFT_DELETE_PATCH = Path.of(
            "src/main/resources/db/patches/20260711_admin_soft_delete_columns.sql");
    private static final Path ACCOUNT_GUARD_MAPPER = Path.of(
            "src/main/resources/mapper/admin/security/AdminAccountGuardMapper.xml");
    private static final Path CREATE_SUPERADMIN = Path.of(
            "src/main/resources/db/maintenance/create_verified_developer_superadmin.sql");
    private static final Path GRANT_SUPERADMIN = Path.of(
            "src/main/resources/db/maintenance/grant_verified_developer_superadmin.sql");
    private static final Path REVOKE_SUPERADMIN = Path.of(
            "src/main/resources/db/maintenance/revoke_verified_developer_superadmin.sql");
    private static final Path VERIFY_SUPERADMIN_QUORUM = Path.of(
            "src/main/resources/db/maintenance/verify_superadmin_quorum.sql");

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
                .contains("@ct_nonseed_active_superadmin_count >= 3")
                .contains("privileged.email_verified = 1")
                .contains("privileged.password_enabled = 1")
                .contains("CHAR_LENGTH(privileged.password) = 60")
                .contains("privileged.password REGEXP '^\\\\$2[aby]\\\\$1[0-4]\\\\$[./A-Za-z0-9]{53}$'")
                .contains("privileged.password <> '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja'")
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

    @Test
    void crudCatalogRerunPreservesMutableAuthorizationState() throws Exception {
        String patch = Files.readString(CRUD_CATALOG_PATCH);

        assertThat(patch)
                .contains("@ct_catalog_initialized")
                .contains("WHERE @ct_catalog_initialized = 0")
                .contains("active = IF(@ct_catalog_initialized = 1, active, VALUES(active))")
                .contains("deleted_at = IF(@ct_catalog_initialized = 1, deleted_at, NULL)")
                .contains("exact_permission_total_count")
                .contains("exact_permission_active_count")
                .contains("canonical_stored_item_count")
                .contains("canonical_active_item_count");
    }

    @Test
    void softDeletePatchKeepsLegalFkCoveredAndVerifiesSemanticInvariants() throws Exception {
        String schema = Files.readString(SCHEMA);
        String patch = Files.readString(SOFT_DELETE_PATCH);
        String addRestrict = "ADD CONSTRAINT fk_legal_clause_ver_restrict";
        String dropLegacy = "DROP FOREIGN KEY fk_legal_clause_ver";

        assertThat(schema)
                .contains("CONSTRAINT `fk_legal_clause_ver_restrict`")
                .doesNotContain("CONSTRAINT `fk_legal_clause_ver` FOREIGN");
        assertThat(patch)
                .contains(addRestrict)
                .contains(dropLegacy)
                .contains("@ct_admin_soft_delete_verification_ok")
                .contains("CHECK (guard_ok = 1)")
                .contains("@ct_legal_restrict_fk_valid = 1")
                .contains("@ct_legal_draft_expression_valid = 1");
        assertThat(patch.indexOf(addRestrict)).isLessThan(patch.indexOf(dropLegacy));
    }

    @Test
    void safeSuperAdminSqlRequiresAParseableBcryptShape() throws Exception {
        for (Path path : new Path[] {
                ACCOUNT_GUARD_MAPPER,
                ROLE_PATCH,
                CREATE_SUPERADMIN,
                GRANT_SUPERADMIN,
                REVOKE_SUPERADMIN,
                VERIFY_SUPERADMIN_QUORUM
        }) {
            assertThat(Files.readString(path))
                    .as(path.toString())
                    .contains("REGEXP '^\\\\$2[aby]\\\\$1[0-4]\\\\$[./A-Za-z0-9]{53}$'")
                    .doesNotContain("password LIKE '$2%'")
                    .doesNotContain("password NOT LIKE '$2%'");
        }
    }
}
