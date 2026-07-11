package com.careertuner.admin.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.grid.AdminListRequest;
import com.careertuner.admin.common.grid.BulkActionResult;
import com.careertuner.admin.common.grid.BulkRequest;
import com.careertuner.admin.common.grid.ExportColumn;
import com.careertuner.admin.common.grid.ExportFormat;
import com.careertuner.admin.common.grid.ExportScope;
import com.careertuner.admin.common.grid.GridExporter;
import com.careertuner.admin.common.grid.PageResult;
import com.careertuner.admin.user.dto.AdminUserDetail;
import com.careertuner.admin.user.dto.AdminUserCreateRequest;
import com.careertuner.admin.user.dto.AdminUserLoginHistoryRow;
import com.careertuner.admin.user.dto.AdminUserRow;
import com.careertuner.admin.user.dto.AdminUserStatusUpdateRequest;
import com.careertuner.admin.user.service.AdminUserService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequireAdminPermission({"USER_READ"})
@RequiredArgsConstructor
public class AdminUserController {

    /** 내보내기 컬럼 정의 — CSV/Excel 겸용(GridExporter). */
    private static final List<ExportColumn<AdminUserRow>> EXPORT_COLUMNS = List.of(
            ExportColumn.of("ID", AdminUserRow::getId),
            ExportColumn.of("이메일", AdminUserRow::getEmail),
            ExportColumn.of("이름", AdminUserRow::getName),
            ExportColumn.of("상태", AdminUserRow::getStatus),
            ExportColumn.of("권한", AdminUserRow::getRole),
            ExportColumn.of("요금제", AdminUserRow::getPlan),
            ExportColumn.of("크레딧", AdminUserRow::getCredit),
            ExportColumn.of("이메일 인증", AdminUserRow::isEmailVerified),
            ExportColumn.of("로그인 성공", AdminUserRow::getLoginSuccessCount),
            ExportColumn.of("로그인 실패", AdminUserRow::getLoginFailCount),
            ExportColumn.of("연속 실패", AdminUserRow::getFailedLoginCount),
            ExportColumn.of("최근 로그인", AdminUserRow::getLastLoginAt),
            ExportColumn.of("차단 만료", AdminUserRow::getBlockedUntil),
            ExportColumn.of("차단 사유", AdminUserRow::getBlockedReason),
            ExportColumn.of("가입일", AdminUserRow::getCreatedAt));

    private final AdminUserService service;

    /** 일반 회원 생성. 관리자 역할 생성·승격은 /api/admin/super 경로에서만 수행한다. */
    @PostMapping
    @RequireAdminPermission({"USER_CREATE"})
    public ApiResponse<AdminUserRow> create(@AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody AdminUserCreateRequest request) {
        return ApiResponse.ok(service.create(authUser, request));
    }

    @GetMapping
    public ApiResponse<List<AdminUserRow>> users(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.users(authUser, keyword, status, role, limit));
    }

    /** 공통 그리드 계약 목록(서버 페이징/정렬/검색/CLIENT 전량 모드). */
    @GetMapping("/page")
    public ApiResponse<PageResult<AdminUserRow>> page(@AuthenticationPrincipal AuthUser authUser,
                                                      @ModelAttribute AdminListRequest request) {
        return ApiResponse.ok(service.search(authUser, request));
    }

    /** 내보내기 — scope=all|search|selected|page, format=csv|excel. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser authUser,
                                         @ModelAttribute AdminListRequest request,
                                         @RequestParam(defaultValue = "search") String scope,
                                         @RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(required = false) List<Long> ids) {
        List<AdminUserRow> rows = service.exportRows(authUser, request, ExportScope.parse(scope), ids);
        return GridExporter.download("careertuner-users", ExportFormat.parse(format), EXPORT_COLUMNS, rows);
    }

    /** 일괄 작업 — 현재는 status(상태 일괄 변경)만 지원. */
    @PostMapping("/bulk/{action}")
    @RequireAdminPermission({"USER_UPDATE"})
    public ApiResponse<BulkActionResult> bulk(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable String action,
                                              @RequestBody BulkRequest request) {
        if (!"status".equals(action)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 일괄 작업입니다.");
        }
        return ApiResponse.ok(service.bulkStatus(authUser, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetail> detail(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long id) {
        return ApiResponse.ok(service.detail(authUser, id));
    }

    @GetMapping("/{id}/login-history")
    public ApiResponse<List<AdminUserLoginHistoryRow>> loginHistory(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.loginHistory(authUser, id, limit));
    }

    @PatchMapping("/{id}/status")
    @RequireAdminPermission({"USER_UPDATE"})
    public ApiResponse<AdminUserRow> updateStatus(@AuthenticationPrincipal AuthUser authUser,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ApiResponse.ok(service.updateStatus(authUser, id, request));
    }

    /** 회원 삭제는 행을 제거하지 않고 status=DELETED/deleted_at으로 기록한다. */
    @DeleteMapping("/{id}")
    @RequireAdminPermission({"USER_DELETE"})
    public ApiResponse<AdminUserRow> delete(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long id,
                                            @RequestParam(required = false) String reason) {
        return ApiResponse.ok(service.softDelete(authUser, id, reason));
    }

    /** 선택 회원 일괄 소프트 삭제. 일반 상태 변경 endpoint에서는 DELETED를 받지 않는다. */
    @PostMapping("/bulk-delete")
    @RequireAdminPermission({"USER_DELETE"})
    public ApiResponse<BulkActionResult> bulkDelete(@AuthenticationPrincipal AuthUser authUser,
                                                    @RequestBody BulkRequest request) {
        return ApiResponse.ok(service.bulkSoftDelete(authUser, request));
    }
}
