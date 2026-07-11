package com.careertuner.common.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careertuner.admin.permission.mapper.EffectivePermissionMapper;
import com.careertuner.admin.reward.service.AdminRewardService;
import com.careertuner.admin.runtimesetting.AdminRuntimeSettingService;
import com.careertuner.admin.securityops.service.AdminSecurityOpsService;
import com.careertuner.admin.settings.service.SettingsExportService;
import com.careertuner.admin.staffgrade.service.AdminStaffGradeService;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityBoundaryMockMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider tokenProvider;

    @MockitoBean UserMapper userMapper;
    @MockitoBean EffectivePermissionMapper permissionMapper;
    @MockitoBean AdminSecurityOpsService securityOpsService;
    @MockitoBean AdminRuntimeSettingService runtimeSettingService;
    @MockitoBean SettingsExportService settingsExportService;
    @MockitoBean AdminRewardService rewardService;
    @MockitoBean AdminStaffGradeService staffGradeService;
    @MockitoBean CommunityPostMapper communityPostMapper;

    @BeforeEach
    void stubPermissions() {
        when(permissionMapper.findEffectivePermissionCodes(7L)).thenReturn(List.of());
    }

    @Test
    void anonymousAndRegularUserReceiveStandard401And403() throws Exception {
        mockMvc.perform(get("/api/admin/me/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String userToken = tokenProvider.createAccessToken(7L, "user@test.dev", "USER");
        when(userMapper.findById(7L)).thenReturn(user(7L, "USER", "ACTIVE"));
        mockMvc.perform(get("/api/admin/me/permissions")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void activeAdminPassesButRoleDriftInvalidatesExistingToken() throws Exception {
        String adminToken = tokenProvider.createAccessToken(7L, "admin@test.dev", "ADMIN");
        when(userMapper.findById(7L)).thenReturn(user(7L, "ADMIN", "ACTIVE"));
        mockMvc.perform(get("/api/admin/me/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        when(userMapper.findById(7L)).thenReturn(user(7L, "USER", "ACTIVE"));
        mockMvc.perform(get("/api/admin/me/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void readOnlySecurityAuditorCanReadButMutationIsForbidden() throws Exception {
        String token = tokenProvider.createAccessToken(17L, "auditor@test.dev", "ADMIN");
        when(userMapper.findById(17L)).thenReturn(user(17L, "ADMIN", "ACTIVE"));
        when(permissionMapper.findEffectivePermissionCodes(17L))
                .thenReturn(List.of("SECURITY_READ"));

        mockMvc.perform(get("/api/admin/security/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/security/waf-sync/process")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(securityOpsService, never()).processWafSyncNow(any());
    }

    @Test
    void regularSecurityAdminCannotReadOrMutateProviderConfiguration() throws Exception {
        String token = tokenProvider.createAccessToken(27L, "security@test.dev", "ADMIN");
        when(userMapper.findById(27L)).thenReturn(user(27L, "ADMIN", "ACTIVE"));
        when(permissionMapper.findEffectivePermissionCodes(27L))
                .thenReturn(List.of("SECURITY_READ", "SECURITY_UPDATE"));

        mockMvc.perform(get("/api/admin/security/providers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/admin/security/providers/TEST_WAF")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Test WAF","providerType":"WAF","mode":"HTTP","enabled":true}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/admin/security/providers/TEST_WAF/health-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(securityOpsService, never()).providers(any(), any(), any());
        verify(securityOpsService, never()).updateProvider(any(), any(), any());
        verify(securityOpsService, never()).runProviderHealthCheck(any(), any());
    }

    @Test
    void superAdminCanReadProviderConfiguration() throws Exception {
        String token = tokenProvider.createAccessToken(28L, "super@test.dev", "SUPER_ADMIN");
        when(userMapper.findById(28L)).thenReturn(user(28L, "SUPER_ADMIN", "ACTIVE"));
        when(securityOpsService.providers(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/security/providers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void regularPolicyAdminCannotReadOrMutateSensitiveRuntimeSettings() throws Exception {
        String token = tokenProvider.createAccessToken(29L, "policy@test.dev", "ADMIN");
        when(userMapper.findById(29L)).thenReturn(user(29L, "ADMIN", "ACTIVE"));
        when(permissionMapper.findEffectivePermissionCodes(29L))
                .thenReturn(List.of("POLICY_READ", "POLICY_UPDATE"));

        mockMvc.perform(get("/api/admin/runtime-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/admin/runtime-settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settingKey":"oauth.client-secret","settingValue":"secret","secret":true}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/settings/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/admin/settings/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaVersion\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(runtimeSettingService, settingsExportService);
    }

    @Test
    void billingUpdateAloneCannotIssueNewCouponEntitlement() throws Exception {
        String token = tokenProvider.createAccessToken(30L, "billing@test.dev", "ADMIN");
        when(userMapper.findById(30L)).thenReturn(user(30L, "ADMIN", "ACTIVE"));
        when(permissionMapper.findEffectivePermissionCodes(30L))
                .thenReturn(List.of("BILLING_READ", "BILLING_UPDATE"));

        mockMvc.perform(post("/api/admin/rewards/coupons/3/issue")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(rewardService, never()).issueCoupon(any(), any(), any());
    }

    @Test
    void regularPolicyAdminCannotReadSalaryConsole() throws Exception {
        String token = tokenProvider.createAccessToken(32L, "policy-pay@test.dev", "ADMIN");
        when(userMapper.findById(32L)).thenReturn(user(32L, "ADMIN", "ACTIVE"));
        when(permissionMapper.findEffectivePermissionCodes(32L))
                .thenReturn(List.of("POLICY_READ", "POLICY_UPDATE"));

        mockMvc.perform(get("/api/admin/staff-grades")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(staffGradeService);
    }

    @Test
    void contentUpdateCannotDeleteOrRestoreDeletedCommunityPost() throws Exception {
        String token = tokenProvider.createAccessToken(31L, "content@test.dev", "ADMIN");
        when(userMapper.findById(31L)).thenReturn(user(31L, "ADMIN", "ACTIVE"));
        when(permissionMapper.findEffectivePermissionCodes(31L))
                .thenReturn(List.of("CONTENT_READ", "CONTENT_UPDATE"));

        mockMvc.perform(delete("/api/admin/community/posts/41")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        when(communityPostMapper.findById(41L)).thenReturn(CommunityPost.builder()
                .id(41L).userId(90L).status("PUBLISHED").build());
        mockMvc.perform(patch("/api/admin/community/posts/41/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\",\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        when(communityPostMapper.findById(42L)).thenReturn(CommunityPost.builder()
                .id(42L).userId(90L).status("DELETED").build());
        mockMvc.perform(patch("/api/admin/community/posts/42/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\",\"reason\":\"test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        verify(communityPostMapper, never()).updateStatus(any(), any());
    }

    private static User user(Long id, String role, String status) {
        return User.builder().id(id).role(role).status(status).build();
    }
}
