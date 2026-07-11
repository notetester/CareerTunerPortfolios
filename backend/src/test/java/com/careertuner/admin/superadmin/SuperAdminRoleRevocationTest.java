package com.careertuner.admin.superadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.common.security.AdminAccountMutationGuard;
import com.careertuner.admin.common.security.AdminAccountState;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.superadmin.dto.AdminAccountRow;
import com.careertuner.admin.superadmin.mapper.PermissionRequestMapper;
import com.careertuner.admin.superadmin.mapper.SuperAdminMapper;
import com.careertuner.admin.superadmin.service.SuperAdminService;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.security.AuthUser;

class SuperAdminRoleRevocationTest {

    @Test
    void roleChangeRevokesAllRefreshTokens() {
        SuperAdminMapper mapper = mock(SuperAdminMapper.class);
        AdminActionLogService actionLog = mock(AdminActionLogService.class);
        PermissionRequestMapper requestMapper = mock(PermissionRequestMapper.class);
        AuthMapper authMapper = mock(AuthMapper.class);
        AdminAccountMutationGuard guard = mock(AdminAccountMutationGuard.class);
        SuperAdminService service = new SuperAdminService(mapper, actionLog, requestMapper, authMapper, guard);
        AuthUser actor = new AuthUser(1L, "super@test.dev", "SUPER_ADMIN");
        AdminAccountRow target = new AdminAccountRow();
        target.setId(2L);
        target.setRole("ADMIN");
        target.setStatus("ACTIVE");

        when(guard.validateRoleChange(actor, 2L, "USER"))
                .thenReturn(new AdminAccountState(2L, "ADMIN", "ACTIVE"));
        when(mapper.findAdmin(2L)).thenReturn(target);
        when(mapper.findUserPermissions(2L)).thenReturn(List.of());
        when(mapper.findUserGroups(2L)).thenReturn(List.of());

        AdminAccountRow result = service.updateRole(actor, 2L, "USER", "권한 회수");

        assertThat(result).isSameAs(target);
        verify(mapper).updateRole(2L, "USER");
        verify(authMapper).revokeAllForUser(2L);
    }
}
