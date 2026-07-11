package com.careertuner.admin.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.careertuner.admin.permission.annotation.AdminRoleOnly;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.permission.service.EffectivePermissionService;
import com.careertuner.admin.permission.web.AdminPermissionInterceptor;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;

class AdminPermissionInterceptorTest {

    private final EffectivePermissionService permissions = mock(EffectivePermissionService.class);
    private final AdminPermissionInterceptor interceptor = new AdminPermissionInterceptor(permissions);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingPermissionDeclarationIsDeniedForAdmin() throws Exception {
        authenticate("ADMIN");

        assertThatThrownBy(() -> invoke("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("정책이 선언되지 않았습니다");
    }

    @Test
    void explicitRoleOnlyAndSuperAdminBypassContinue() throws Exception {
        authenticate("ADMIN");
        assertThat(invoke("roleOnly")).isTrue();

        authenticate("SUPER_ADMIN");
        assertThat(invoke("missing")).isTrue();
    }

    @Test
    void declaredPermissionRequiresEffectiveGrant() throws Exception {
        authenticate("ADMIN");
        when(permissions.hasAny(9L, "BILLING_UPDATE")).thenReturn(false, true);

        assertThatThrownBy(() -> invoke("billingWrite")).isInstanceOf(BusinessException.class);
        assertThat(invoke("billingWrite")).isTrue();
    }

    @Test
    void methodWritePermissionDoesNotReplaceClassReadPermission() throws Exception {
        authenticate("ADMIN");
        when(permissions.hasAny(9L, "BILLING_READ")).thenReturn(false, true);
        when(permissions.hasAny(9L, "BILLING_UPDATE")).thenReturn(true);

        assertThatThrownBy(() -> invoke(new ReadWriteProbeController(), "update"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BILLING_READ");
        assertThat(invoke(new ReadWriteProbeController(), "update")).isTrue();
    }

    private boolean invoke(String methodName) throws Exception {
        return invoke(new ProbeController(), methodName);
    }

    private boolean invoke(Object controller, String methodName) throws Exception {
        HandlerMethod handler = new HandlerMethod(controller,
                controller.getClass().getMethod(methodName));
        return interceptor.preHandle(new MockHttpServletRequest("GET", "/api/admin/probe"),
                new MockHttpServletResponse(), handler);
    }

    private static void authenticate(String role) {
        AuthUser authUser = new AuthUser(9L, "admin@test.dev", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                authUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    static class ProbeController {
        public void missing() {
        }

        @AdminRoleOnly
        public void roleOnly() {
        }

        @RequireAdminPermission({"BILLING_UPDATE"})
        public void billingWrite() {
        }
    }

    @RequireAdminPermission({"BILLING_READ"})
    static class ReadWriteProbeController {
        @RequireAdminPermission({"BILLING_UPDATE"})
        public void update() {
        }
    }
}
