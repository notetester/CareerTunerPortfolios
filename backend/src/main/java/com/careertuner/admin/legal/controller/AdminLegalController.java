package com.careertuner.admin.legal.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.legal.dto.AdminLegalVersionDetail;
import com.careertuner.admin.legal.dto.AdminLegalVersionResponse;
import com.careertuner.admin.legal.dto.CreateLegalDraftRequest;
import com.careertuner.admin.legal.dto.PublishLegalRequest;
import com.careertuner.admin.legal.dto.PublishLegalResponse;
import com.careertuner.admin.legal.dto.SaveLegalDraftRequest;
import com.careertuner.admin.legal.service.AdminLegalService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 법적 문서 관리 (ADMIN 자동 — SecurityConfig /api/admin/** hasRole(ADMIN)).
 */
@RestController
@RequestMapping("/api/admin/legal")
@RequireAdminPermission({"CONTENT_MANAGE", "CONTENT_ADMIN", "POLICY_MANAGE", "POLICY_ADMIN"})
@RequiredArgsConstructor
public class AdminLegalController {

    private final AdminLegalService adminLegalService;

    @GetMapping("/{docType}/versions")
    public ApiResponse<List<AdminLegalVersionResponse>> getVersions(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable String docType) {
        return ApiResponse.ok(adminLegalService.getVersions(authUser, docType));
    }

    @GetMapping("/versions/{id}")
    public ApiResponse<AdminLegalVersionDetail> getVersionDetail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(adminLegalService.getVersionDetail(authUser, id));
    }

    @PostMapping("/{docType}/versions")
    public ApiResponse<AdminLegalVersionDetail> createDraft(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable String docType,
            @Valid @RequestBody(required = false) CreateLegalDraftRequest request) {
        return ApiResponse.ok(adminLegalService.createDraft(authUser, docType, request));
    }

    @PutMapping("/versions/{id}")
    public ApiResponse<AdminLegalVersionDetail> saveDraft(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody SaveLegalDraftRequest request) {
        return ApiResponse.ok(adminLegalService.saveDraft(authUser, id, request));
    }

    @PostMapping("/versions/{id}/publish")
    public ApiResponse<PublishLegalResponse> publish(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody(required = false) PublishLegalRequest request) {
        return ApiResponse.ok(adminLegalService.publish(authUser, id, request));
    }

    @DeleteMapping("/versions/{id}")
    public ApiResponse<Void> deleteVersion(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        adminLegalService.deleteVersion(authUser, id);
        return ApiResponse.ok();
    }
}
