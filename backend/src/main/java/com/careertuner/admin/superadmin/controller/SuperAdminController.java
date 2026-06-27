package com.careertuner.admin.superadmin.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.superadmin.dto.AdminAccountRow;
import com.careertuner.admin.superadmin.dto.AdminAssignmentRequest;
import com.careertuner.admin.superadmin.dto.AdminGroupRequest;
import com.careertuner.admin.superadmin.dto.AdminPermissionAuditRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionGroupRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionPolicyRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionRequest;
import com.careertuner.admin.superadmin.dto.AdminRoleRequest;
import com.careertuner.admin.superadmin.service.SuperAdminService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/super")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService service;

    @GetMapping("/admins")
    public ApiResponse<List<AdminAccountRow>> admins(@AuthenticationPrincipal AuthUser authUser,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String sortBy,
                                                     @RequestParam(required = false) String sortDir,
                                                     @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.admins(authUser, keyword, sortBy, sortDir, limit));
    }

    @GetMapping("/users/search")
    public ApiResponse<List<AdminAccountRow>> searchUsers(@AuthenticationPrincipal AuthUser authUser,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String sortBy,
                                                          @RequestParam(required = false) String sortDir,
                                                          @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.searchUsers(authUser, keyword, sortBy, sortDir, limit));
    }

    @GetMapping("/admins/{userId}")
    public ApiResponse<AdminAccountRow> admin(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long userId) {
        return ApiResponse.ok(service.admin(authUser, userId));
    }

    @PatchMapping("/admins/{userId}/role")
    public ApiResponse<AdminAccountRow> updateRole(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Long userId,
                                                   @Valid @RequestBody AdminRoleRequest request) {
        return ApiResponse.ok(service.updateRole(authUser, userId, request.role(), request.reason()));
    }

    @PostMapping("/admins/{userId}/permissions")
    public ApiResponse<AdminAccountRow> grantPermission(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long userId,
                                                        @Valid @RequestBody AdminAssignmentRequest request) {
        return ApiResponse.ok(service.grantPermission(authUser, userId, request.code(), request.reason()));
    }

    @PatchMapping("/admins/{userId}/permissions/revoke")
    public ApiResponse<AdminAccountRow> revokePermission(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long userId,
                                                         @Valid @RequestBody AdminAssignmentRequest request) {
        return ApiResponse.ok(service.revokePermission(authUser, userId, request.code(), request.reason()));
    }

    @PostMapping("/admins/{userId}/groups")
    public ApiResponse<AdminAccountRow> assignGroup(@AuthenticationPrincipal AuthUser authUser,
                                                    @PathVariable Long userId,
                                                    @Valid @RequestBody AdminAssignmentRequest request) {
        return ApiResponse.ok(service.assignGroup(authUser, userId, request.code(), request.reason()));
    }

    @PatchMapping("/admins/{userId}/groups/revoke")
    public ApiResponse<AdminAccountRow> revokeGroup(@AuthenticationPrincipal AuthUser authUser,
                                                    @PathVariable Long userId,
                                                    @Valid @RequestBody AdminAssignmentRequest request) {
        return ApiResponse.ok(service.revokeGroup(authUser, userId, request.code(), request.reason()));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<AdminPermissionPolicyRow>> permissions(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.permissions(authUser));
    }

    @PostMapping("/permissions")
    public ApiResponse<Void> createPermission(@AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody AdminPermissionRequest request) {
        service.createPermission(authUser, request);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/permissions/{code}/toggle")
    public ApiResponse<Void> togglePermission(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable String code,
                                              @RequestParam boolean active) {
        service.togglePermission(authUser, code, active);
        return ApiResponse.ok(null);
    }

    @GetMapping("/groups")
    public ApiResponse<List<AdminPermissionGroupRow>> groups(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.groups(authUser));
    }

    @PostMapping("/groups")
    public ApiResponse<Void> createGroup(@AuthenticationPrincipal AuthUser authUser,
                                         @Valid @RequestBody AdminGroupRequest request) {
        service.createGroup(authUser, request);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/groups/{code}/toggle")
    public ApiResponse<Void> toggleGroup(@AuthenticationPrincipal AuthUser authUser,
                                         @PathVariable String code,
                                         @RequestParam boolean active) {
        service.toggleGroup(authUser, code, active);
        return ApiResponse.ok(null);
    }

    @PostMapping("/groups/{groupCode}/permissions/{permissionCode}")
    public ApiResponse<Void> addGroupItem(@AuthenticationPrincipal AuthUser authUser,
                                          @PathVariable String groupCode,
                                          @PathVariable String permissionCode) {
        service.addGroupItem(authUser, groupCode, permissionCode);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/groups/{groupCode}/permissions/{permissionCode}/remove")
    public ApiResponse<Void> removeGroupItem(@AuthenticationPrincipal AuthUser authUser,
                                             @PathVariable String groupCode,
                                             @PathVariable String permissionCode) {
        service.removeGroupItem(authUser, groupCode, permissionCode);
        return ApiResponse.ok(null);
    }

    @GetMapping("/audit")
    public ApiResponse<List<AdminPermissionAuditRow>> audit(@AuthenticationPrincipal AuthUser authUser,
                                                            @RequestParam(required = false) Long userId,
                                                            @RequestParam(required = false) String sortBy,
                                                            @RequestParam(required = false) String sortDir,
                                                            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.audit(authUser, userId, sortBy, sortDir, limit));
    }
}
