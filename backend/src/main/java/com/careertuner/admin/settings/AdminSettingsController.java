package com.careertuner.admin.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.settings.dto.SettingsExport;
import com.careertuner.admin.settings.dto.SettingsImportResult;
import com.careertuner.admin.settings.service.SettingsExportService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 설정 export/import 콘솔 API. 런타임 설정·중재 정책을 섹션별 JSON 으로
 * 백업/이관한다(환경 승격·재해 복구용). TripTogether {@code AdminInitialSettingsController} 이식.
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequireAdminPermission({"POLICY_READ"})
@RequiredArgsConstructor
public class AdminSettingsController {

    private final SettingsExportService service;
    private final AdminActionLogService actionLogService;

    /** 지원 섹션 키 목록(콘솔 체크박스용). */
    @GetMapping("/sections")
    public ApiResponse<List<String>> sections(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireSuperAdmin(authUser);
        return ApiResponse.ok(List.copyOf(SettingsExportService.SECTION_KEYS));
    }

    /** 설정 export. sections 미지정 시 전체. */
    @GetMapping("/export")
    public ApiResponse<SettingsExport> export(@AuthenticationPrincipal AuthUser authUser,
                                              @RequestParam(required = false) String sections) {
        AdminAccess.requireSuperAdmin(authUser);
        return ApiResponse.ok(service.export(authUser, parseSections(sections)));
    }

    /** 설정 import(upsert). 적용/스킵 건수·사유를 반환하고 감사 로그를 남긴다. */
    @PostMapping("/import")
    @RequireAdminPermission({"POLICY_UPDATE"})
    public ApiResponse<SettingsImportResult> importSettings(@AuthenticationPrincipal AuthUser authUser,
                                                            @RequestBody SettingsExport payload) {
        AdminAccess.requireSuperAdmin(authUser);
        SettingsImportResult result = service.importSettings(authUser, payload);
        actionLogService.record(authUser, null, "SETTINGS_IMPORTED", "ADMIN_SETTINGS",
                null,
                "applied=" + result.totalApplied() + ", skipped=" + result.totalSkipped(),
                "설정 import");
        return ApiResponse.ok(result);
    }

    private static Set<String> parseSections(String sections) {
        if (sections == null || sections.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(sections.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
