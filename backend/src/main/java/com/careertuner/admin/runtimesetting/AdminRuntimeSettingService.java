package com.careertuner.admin.runtimesetting;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.security.AuthUser;
import com.careertuner.runtimesetting.domain.RuntimeSetting;
import com.careertuner.runtimesetting.domain.RuntimeSettingHistory;
import com.careertuner.runtimesetting.service.RuntimeSettingService;

import lombok.RequiredArgsConstructor;

/** 민감한 런타임 설정에 SUPER_ADMIN 경계를 적용하는 관리자 전용 파사드. */
@Service
@RequiredArgsConstructor
public class AdminRuntimeSettingService {

    private final RuntimeSettingService delegate;

    public List<RuntimeSetting> list(
            AuthUser authUser, String group, String keyword, boolean includeInactive) {
        AdminAccess.requireSuperAdmin(authUser);
        return delegate.getRuntimeSettings(group, keyword, includeInactive);
    }

    public RuntimeSetting save(AuthUser authUser, RuntimeSetting input, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        return delegate.saveRuntimeSetting(input, authUser.id(), reason);
    }

    public List<RuntimeSettingHistory> history(AuthUser authUser, String key, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        return delegate.getHistories(key, limit);
    }
}
