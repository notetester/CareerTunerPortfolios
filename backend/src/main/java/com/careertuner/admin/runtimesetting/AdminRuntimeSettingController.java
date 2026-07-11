package com.careertuner.admin.runtimesetting;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.runtimesetting.domain.RuntimeSetting;
import com.careertuner.runtimesetting.domain.RuntimeSettingHistory;

import lombok.RequiredArgsConstructor;

/** SUPER_ADMIN 전용 key-value 런타임 설정 콘솔 API. */
@RestController
@RequestMapping("/api/admin/runtime-settings")
@RequireAdminPermission({"POLICY_READ"})
@RequiredArgsConstructor
public class AdminRuntimeSettingController {

    private final AdminRuntimeSettingService service;

    /** 설정 저장 요청. secret/editable/active 는 미지정 시 각각 false/true/true. */
    public record SaveRequest(
            String settingKey,
            String settingGroup,
            String displayName,
            String settingValue,
            String fallbackValue,
            String valueType,
            Boolean secret,
            Boolean editable,
            Boolean active,
            String description,
            // reason: 관리자가 이 설정을 바꾼 사유(자유 텍스트, 선택)
            String reason) {
    }

    @GetMapping
    public ApiResponse<List<RuntimeSetting>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        AdminAccess.requireSuperAdmin(authUser);
        return ApiResponse.ok(service.list(authUser, group, keyword, includeInactive));
    }

    @PostMapping
    @RequireAdminPermission({"POLICY_UPDATE"})
    public ApiResponse<RuntimeSetting> save(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestBody SaveRequest request) {
        AdminAccess.requireSuperAdmin(authUser);
        RuntimeSetting input = RuntimeSetting.builder()
                .settingKey(request.settingKey())
                .settingGroup(request.settingGroup())
                .displayName(request.displayName())
                .settingValue(request.settingValue())
                .fallbackValue(request.fallbackValue())
                .valueType(request.valueType())
                .secret(Boolean.TRUE.equals(request.secret()))
                .editable(request.editable() == null || request.editable())
                .active(request.active() == null || request.active())
                .description(request.description())
                .build();
        // reason: 요청에 담긴 변경 사유를 이력에 함께 전달
        return ApiResponse.ok(service.save(authUser, input, request.reason()));
    }

    @GetMapping("/history")
    public ApiResponse<List<RuntimeSettingHistory>> history(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "100") int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        return ApiResponse.ok(service.history(authUser, key, limit));
    }
}
