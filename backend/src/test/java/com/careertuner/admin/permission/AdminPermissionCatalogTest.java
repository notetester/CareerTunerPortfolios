package com.careertuner.admin.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.permission.catalog.AdminPermissionCatalog;

class AdminPermissionCatalogTest {

    @Test
    void catalogContainsOnlyExactDomainActionCodes() {
        assertThat(AdminPermissionCatalog.codes()).hasSize(29);
        assertThat(AdminPermissionCatalog.definitions())
                .allSatisfy(definition -> assertThat(definition.code())
                        .isEqualTo(definition.domain().name() + "_" + definition.action().name()));
    }

    @Test
    void normalAdminCannotReceivePermissionGovernanceCodes() {
        assertThat(AdminPermissionCatalog.adminAssignableCodes())
                .noneMatch(code -> code.startsWith("ADMIN_PERMISSION_"))
                .contains("USER_DELETE", "SECURITY_DELETE", "BILLING_DELETE",
                        "CONTENT_DELETE", "AI_DELETE", "POLICY_DELETE")
                .contains("AUDIT_READ");
    }
}
