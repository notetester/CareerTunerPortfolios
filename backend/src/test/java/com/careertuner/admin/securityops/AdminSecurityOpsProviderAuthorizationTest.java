package com.careertuner.admin.securityops;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.securityops.dto.SecurityProviderConfigRequest;
import com.careertuner.admin.securityops.engine.BlockRuleCacheService;
import com.careertuner.admin.securityops.mapper.AdminSecurityOpsMapper;
import com.careertuner.admin.securityops.service.AdminSecurityOpsService;
import com.careertuner.admin.securityops.waf.WafSyncScheduler;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;

@ExtendWith(MockitoExtension.class)
class AdminSecurityOpsProviderAuthorizationTest {

    @Mock AdminSecurityOpsMapper mapper;
    @Mock AdminActionLogService actionLogService;
    @Mock BlockRuleCacheService blockRuleCacheService;
    @Mock WafSyncScheduler wafSyncScheduler;
    @InjectMocks AdminSecurityOpsService service;

    @Test
    void regularAdminCannotReadUpdateOrHealthCheckProviderConfiguration() {
        AuthUser admin = new AuthUser(7L, "admin@test.dev", "ADMIN");
        SecurityProviderConfigRequest request = new SecurityProviderConfigRequest(
                "Test WAF", "WAF", "HTTP", true, "https://waf.example.com", "{}");

        assertThatThrownBy(() -> service.providers(admin, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");
        assertThatThrownBy(() -> service.updateProvider(admin, "TEST_WAF", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");
        assertThatThrownBy(() -> service.runProviderHealthCheck(admin, "TEST_WAF"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");

        verifyNoInteractions(mapper, actionLogService, blockRuleCacheService, wafSyncScheduler);
    }
}
