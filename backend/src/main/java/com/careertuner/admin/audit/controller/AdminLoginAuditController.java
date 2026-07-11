package com.careertuner.admin.audit.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.audit.dto.AdminLoginAuditRow;
import com.careertuner.admin.audit.service.AdminLoginAuditService;
import com.careertuner.admin.common.grid.AdminListRequest;
import com.careertuner.admin.common.grid.ExportColumn;
import com.careertuner.admin.common.grid.ExportFormat;
import com.careertuner.admin.common.grid.ExportScope;
import com.careertuner.admin.common.grid.GridExporter;
import com.careertuner.admin.common.grid.PageResult;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 로그인 감사 그리드 API — 조회 전용(목록 + 내보내기). */
@RestController
@RequestMapping("/api/admin/audit/logins")
@RequireAdminPermission({"SECURITY_LOG_READ", "AUDIT_ADMIN"})
@RequiredArgsConstructor
public class AdminLoginAuditController {

    private static final List<ExportColumn<AdminLoginAuditRow>> EXPORT_COLUMNS = List.of(
            ExportColumn.of("ID", AdminLoginAuditRow::getId),
            ExportColumn.of("회원 ID", AdminLoginAuditRow::getUserId),
            ExportColumn.of("회원 이메일", AdminLoginAuditRow::getUserEmail),
            ExportColumn.of("회원 이름", AdminLoginAuditRow::getUserName),
            ExportColumn.of("이벤트", AdminLoginAuditRow::getEventType),
            ExportColumn.of("성공 여부", AdminLoginAuditRow::isSuccess),
            ExportColumn.of("실패 사유", AdminLoginAuditRow::getFailReason),
            ExportColumn.of("제공자", AdminLoginAuditRow::getAuthProvider),
            ExportColumn.of("로그인 방식", AdminLoginAuditRow::getLoginMethod),
            ExportColumn.of("입력 식별자", AdminLoginAuditRow::getLoginIdentifier),
            ExportColumn.of("IP", AdminLoginAuditRow::getIpAddress),
            ExportColumn.of("User-Agent", AdminLoginAuditRow::getUserAgent),
            ExportColumn.of("요청 URI", AdminLoginAuditRow::getRequestUri),
            ExportColumn.of("발생 시각", AdminLoginAuditRow::getCreatedAt));

    private final AdminLoginAuditService service;

    @GetMapping
    public ApiResponse<PageResult<AdminLoginAuditRow>> list(@AuthenticationPrincipal AuthUser authUser,
                                                            @ModelAttribute AdminListRequest request) {
        return ApiResponse.ok(service.search(authUser, request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser authUser,
                                         @ModelAttribute AdminListRequest request,
                                         @RequestParam(defaultValue = "search") String scope,
                                         @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) List<Long> ids) {
        List<AdminLoginAuditRow> rows = service.exportRows(authUser, request, ExportScope.parse(scope), ids);
        return GridExporter.download("careertuner-login-audit", ExportFormat.parse(format), EXPORT_COLUMNS, rows);
    }
}
