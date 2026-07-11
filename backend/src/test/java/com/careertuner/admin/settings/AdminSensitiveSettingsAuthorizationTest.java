package com.careertuner.admin.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.runtimesetting.AdminRuntimeSettingService;
import com.careertuner.admin.settings.service.SettingsExportService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

class AdminSensitiveSettingsAuthorizationTest {

    private final AuthUser admin = new AuthUser(7L, "admin@test.dev", "ADMIN");

    @Test
    void regularAdminCannotReachRuntimeSettingDelegate() {
        RuntimeSettingService delegate = mock(RuntimeSettingService.class);
        AdminRuntimeSettingService service = new AdminRuntimeSettingService(delegate);

        assertThatThrownBy(() -> service.list(admin, null, null, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");
        assertThatThrownBy(() -> service.save(admin, null, "test"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");
        assertThatThrownBy(() -> service.history(admin, null, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");

        verifyNoInteractions(delegate);
    }

    @Test
    void regularAdminCannotExportOrImportSensitiveSettings() {
        RuntimeSettingService runtimeSettingService = mock(RuntimeSettingService.class);
        ModerationSettingService moderationSettingService = mock(ModerationSettingService.class);
        SettingsExportService service = new SettingsExportService(runtimeSettingService, moderationSettingService);

        assertThatThrownBy(() -> service.export(admin, Set.of(SettingsExportService.SEC_RUNTIME)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");
        assertThatThrownBy(() -> service.importSettings(admin, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("슈퍼 관리자 권한이 필요합니다.");

        verifyNoInteractions(runtimeSettingService, moderationSettingService);
    }
}
