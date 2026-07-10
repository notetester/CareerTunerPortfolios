package com.careertuner.admin.staffgrade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.staffgrade.domain.AdminStaffGrade;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportApplyRequest;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeImportRow;
import com.careertuner.admin.staffgrade.dto.AdminStaffGradeUpsertRequest;
import com.careertuner.admin.staffgrade.mapper.AdminStaffGradeMapper;
import com.careertuner.admin.staffgrade.service.AdminStaffGradeService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;

import tools.jackson.databind.ObjectMapper;

class AdminStaffGradeTargetRoleTest {

    private final AdminStaffGradeMapper mapper = mock(AdminStaffGradeMapper.class);
    private final AdminStaffGradeService service = new AdminStaffGradeService(mapper, new ObjectMapper());
    private final AuthUser superAdmin = new AuthUser(1L, "super@test.dev", "SUPER_ADMIN");

    @Test
    void directUpsertRejectsRegularUserAtFinalWriteBoundary() {
        when(mapper.findUserRoleForUpdate(9L)).thenReturn("USER");

        assertThatThrownBy(() -> service.upsert(superAdmin, 9L,
                new AdminStaffGradeUpsertRequest(null, null, null, null, null, null,
                        10_000, "KRW", null, "테스트")))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).upsertGrade(org.mockito.ArgumentMatchers.any(AdminStaffGrade.class));
    }

    @Test
    void applyImportRevalidatesRoleInsteadOfTrustingPreviewUserId() {
        AdminStaffGradeImportRow row = new AdminStaffGradeImportRow();
        row.setUserId(9L);
        row.setStatus("OK");
        when(mapper.findUserRoleForUpdate(9L)).thenReturn("USER");

        assertThatThrownBy(() -> service.applyImport(superAdmin,
                new AdminStaffGradeImportApplyRequest(List.of(row))))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).upsertGrade(org.mockito.ArgumentMatchers.any(AdminStaffGrade.class));
    }

    @Test
    void previewEmailLookupOnlyResolvesCurrentAdminRoles() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/admin/staffgrade/AdminStaffGradeMapper.xml"));

        org.assertj.core.api.Assertions.assertThat(xml)
                .contains("AND role IN ('ADMIN', 'SUPER_ADMIN')")
                .contains("<select id=\"findUserRoleForUpdate\"")
                .contains("FOR UPDATE");
    }
}
