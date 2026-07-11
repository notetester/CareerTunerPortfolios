package com.careertuner.admin.community.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careertuner.admin.common.security.AdminAccountMutationGuard;
import com.careertuner.admin.community.dto.AdminReportActionRequest;
import com.careertuner.admin.community.mapper.AdminReportMapper;
import com.careertuner.admin.permission.service.EffectivePermissionService;
import com.careertuner.admin.user.mapper.AdminUserMapper;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.community.moderation.service.PostModerationService;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AdminReportActionPermissionTest {

    private static final AuthUser ADMIN = new AuthUser(7L, "admin@test.dev", "ADMIN");

    @Mock AdminReportMapper reportMapper;
    @Mock PostAiResultMapper aiResultMapper;
    @Mock PostModerationService moderationService;
    @Mock CommunityCommentMapper commentMapper;
    @Mock CommunityPostMapper postMapper;
    @Mock ObjectMapper objectMapper;
    @Mock AdminUserMapper userMapper;
    @Mock AuthMapper authMapper;
    @Mock NotificationService notificationService;
    @Mock ModerationSettingService settingService;
    @Mock AdminAccountMutationGuard accountMutationGuard;
    @Mock EffectivePermissionService effectivePermissionService;
    @InjectMocks AdminReportServiceImpl service;

    @Test
    void deleteActionRequiresContentDeleteRatherThanContentUpdate() {
        when(effectivePermissionService.hasAny(ADMIN.id(), "CONTENT_DELETE")).thenReturn(false);

        assertThatThrownBy(() -> service.takeAction(
                ADMIN, 1L, new AdminReportActionRequest("DELETED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONTENT_DELETE");

        verify(effectivePermissionService).hasAny(ADMIN.id(), "CONTENT_DELETE");
        verify(effectivePermissionService, never()).hasAny(ADMIN.id(), "CONTENT_UPDATE");
    }

    @Test
    void nonDeleteActionRequiresContentUpdate() {
        when(effectivePermissionService.hasAny(ADMIN.id(), "CONTENT_UPDATE")).thenReturn(false);

        assertThatThrownBy(() -> service.takeAction(
                ADMIN, 1L, new AdminReportActionRequest("HIDDEN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONTENT_UPDATE");

        verify(effectivePermissionService).hasAny(ADMIN.id(), "CONTENT_UPDATE");
        verify(effectivePermissionService, never()).hasAny(ADMIN.id(), "CONTENT_DELETE");
    }

    @Test
    void authorBlockAlsoRequiresUserUpdate() {
        when(effectivePermissionService.hasAny(ADMIN.id(), "CONTENT_UPDATE")).thenReturn(true);
        when(effectivePermissionService.hasAny(ADMIN.id(), "USER_UPDATE")).thenReturn(false);

        assertThatThrownBy(() -> service.takeAction(
                ADMIN, 1L, new AdminReportActionRequest("BLOCK_AUTHOR")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("USER_UPDATE");

        verify(effectivePermissionService).hasAny(ADMIN.id(), "CONTENT_UPDATE");
        verify(effectivePermissionService).hasAny(ADMIN.id(), "USER_UPDATE");
    }
}
