package com.careertuner.admin.superadmin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.common.security.AdminAccountMutationGuard;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.superadmin.dto.AdminGroupRequest;
import com.careertuner.admin.superadmin.dto.AdminPermissionRequest;
import com.careertuner.admin.superadmin.mapper.PermissionRequestMapper;
import com.careertuner.admin.superadmin.mapper.SuperAdminMapper;
import com.careertuner.admin.superadmin.service.SuperAdminService;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;

class SuperAdminCatalogBoundaryTest {

    private final SuperAdminMapper mapper = mock(SuperAdminMapper.class);
    private final SuperAdminService service = new SuperAdminService(
            mapper, mock(AdminActionLogService.class), mock(PermissionRequestMapper.class),
            mock(AuthMapper.class), mock(AdminAccountMutationGuard.class));
    private final AuthUser superAdmin = new AuthUser(1L, "super@test.dev", "SUPER_ADMIN");

    @Test
    void arbitraryPermissionAndGroupCodesAreRejected() {
        assertThatThrownBy(() -> service.createPermission(superAdmin,
                new AdminPermissionRequest("UNMAPPED_PERMISSION", "임의 권한", null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createGroup(superAdmin,
                new AdminGroupRequest("UNMAPPED_GROUP", "임의 그룹", null)))
                .isInstanceOf(BusinessException.class);

        verify(mapper, never()).updatePermissionMetadata(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        verify(mapper, never()).updateGroupMetadata(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void seededCatalogEntriesAllowMetadataUpdateOnly() {
        when(mapper.updatePermissionMetadata("BILLING_UPDATE", "결제 변경", null, 1L)).thenReturn(1);
        when(mapper.updateGroupMetadata("BILLING_ADMIN", "결제 그룹", null, 1L)).thenReturn(1);

        service.createPermission(superAdmin,
                new AdminPermissionRequest("BILLING_UPDATE", "결제 변경", null));
        service.createGroup(superAdmin,
                new AdminGroupRequest("BILLING_ADMIN", "결제 그룹", null));

        verify(mapper).updatePermissionMetadata("BILLING_UPDATE", "결제 변경", null, 1L);
        verify(mapper).updateGroupMetadata("BILLING_ADMIN", "결제 그룹", null, 1L);
    }

    @Test
    void adminScopedGroupRejectsSuperAdminAndDeletePermissions() {
        when(mapper.findGroupRoleScope("MEMBER_ADMIN")).thenReturn("ADMIN");

        assertThatThrownBy(() -> service.addGroupItem(superAdmin, "MEMBER_ADMIN", "ADMIN_PERMISSION_READ"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.addGroupItem(superAdmin, "MEMBER_ADMIN", "USER_DELETE"))
                .isInstanceOf(BusinessException.class);

        verify(mapper, never()).addGroupItem(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void superAdminScopedGroupAllowsFullCatalog() {
        when(mapper.findGroupRoleScope("SUPER_ADMIN_GROUP")).thenReturn("SUPER_ADMIN");

        service.addGroupItem(superAdmin, "SUPER_ADMIN_GROUP", "ADMIN_PERMISSION_DELETE");

        verify(mapper).addGroupItem("SUPER_ADMIN_GROUP", "ADMIN_PERMISSION_DELETE", 1L);
    }
}
