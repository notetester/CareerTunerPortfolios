package com.careertuner.community.moderation.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.permission.service.EffectivePermissionService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.community.moderation.dto.ModerationReviewDecisionRequest;
import com.careertuner.community.moderation.service.AdminModerationBackfillService;
import com.careertuner.community.moderation.service.AdminModerationService;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.community.moderation.service.PostModerationService;

class AdminModerationControllerPermissionTest {

    private final AdminModerationService moderationService = mock(AdminModerationService.class);
    private final EffectivePermissionService permissionService = mock(EffectivePermissionService.class);
    private final AdminModerationController controller = new AdminModerationController(
            mock(PostModerationService.class),
            moderationService,
            mock(AdminModerationBackfillService.class),
            mock(ModerationSettingService.class),
            permissionService);

    @Test
    void keepRequiresOnlyAiUpdateBoundaryHandledByAnnotation() {
        controller.decideReviewQueue(admin(), 10L, new ModerationReviewDecisionRequest("KEEP"));

        verify(permissionService, never()).hasAny(1L, "CONTENT_UPDATE");
        verify(moderationService).decideReviewQueue(1L, 10L, "KEEP");
    }

    @Test
    void hideRequiresContentUpdateInAdditionToAiUpdateAnnotation() {
        when(permissionService.hasAny(1L, "CONTENT_UPDATE")).thenReturn(false);

        assertThatThrownBy(() -> controller.decideReviewQueue(
                admin(), 10L, new ModerationReviewDecisionRequest(" hide ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(moderationService, never()).decideReviewQueue(1L, 10L, " hide ");
    }

    @Test
    void hideProceedsWhenContentUpdateIsGranted() {
        when(permissionService.hasAny(1L, "CONTENT_UPDATE")).thenReturn(true);

        controller.decideReviewQueue(admin(), 10L, new ModerationReviewDecisionRequest("HIDE"));

        verify(moderationService).decideReviewQueue(1L, 10L, "HIDE");
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
