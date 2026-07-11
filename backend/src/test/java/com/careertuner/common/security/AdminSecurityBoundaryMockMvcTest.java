package com.careertuner.common.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careertuner.admin.permission.mapper.EffectivePermissionMapper;
import com.careertuner.admin.securityops.service.AdminSecurityOpsService;
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
                .thenReturn(List.of("SECURITY_LOG_READ", "AUDIT_ADMIN"));

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

    private static User user(Long id, String role, String status) {
        return User.builder().id(id).role(role).status(status).build();
    }
}
