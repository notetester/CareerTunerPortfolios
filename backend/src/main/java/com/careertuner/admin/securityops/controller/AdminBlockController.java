package com.careertuner.admin.securityops.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.admin.securityops.batch.BlockBatchService;
import com.careertuner.admin.securityops.batch.IpBlockBatchRequest;
import com.careertuner.admin.securityops.batch.IpBlockBatchRow;
import com.careertuner.admin.securityops.feed.PolicyFeedImportService;
import com.careertuner.admin.securityops.feed.PolicyFeedModels.PolicyFeedImportRequest;
import com.careertuner.admin.securityops.feed.PolicyFeedModels.PolicyFeedImportResult;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * IP 정책 배치 + 정책기관 피드 대량 업로드 API. AdminSecurityOpsController 와 같은 base path 를 공유한다.
 */
@RestController
@RequestMapping("/api/admin/security")
@RequireAdminPermission({"SECURITY_READ"})
@RequiredArgsConstructor
public class AdminBlockController {

    private final BlockBatchService batchService;
    private final PolicyFeedImportService feedImportService;

    @GetMapping("/block-batches")
    public ApiResponse<List<IpBlockBatchRow>> batches(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(batchService.batches(authUser, keyword, active, limit));
    }

    @GetMapping("/block-batches/{id}")
    public ApiResponse<IpBlockBatchRow> batch(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        return ApiResponse.ok(batchService.batch(authUser, id));
    }

    @PostMapping("/block-batches")
    @RequireAdminPermission({"SECURITY_CREATE"})
    public ApiResponse<IpBlockBatchRow> createBatch(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody IpBlockBatchRequest request) {
        return ApiResponse.ok(batchService.createBatch(authUser, request));
    }

    /** 배치 ON/OFF + cascade 전략(BATCH_ONLY/CASCADE_ACTIVE_RULES/RESTORE_BATCH_CONTROL/FORCE_ENABLE_ALL). */
    @PostMapping("/block-batches/{id}/toggle")
    @RequireAdminPermission({"SECURITY_UPDATE"})
    public ApiResponse<IpBlockBatchRow> toggleBatch(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestParam boolean active,
            @RequestParam(required = false, defaultValue = "BATCH_ONLY") String strategy) {
        return ApiResponse.ok(batchService.toggleBatch(authUser, id, active, strategy));
    }

    /** CSV/JSON 파일 업로드로 정책 피드 대량 import. */
    @PostMapping("/policy-feed/upload")
    @RequireAdminPermission({"SECURITY_CREATE"})
    public ApiResponse<PolicyFeedImportResult> uploadPolicyFeed(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false, defaultValue = "BLOCK") String action,
            @RequestParam(required = false, defaultValue = "SECURITY") String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일을 읽을 수 없습니다: " + e.getMessage());
        }
        String src = sourceName != null && !sourceName.isBlank() ? sourceName : file.getOriginalFilename();
        return ApiResponse.ok(feedImportService.importFromText(authUser, content, null, src, action, category));
    }

    /** JSON 본문(rawText 또는 rows)으로 정책 피드 import. */
    @PostMapping("/policy-feed/import")
    @RequireAdminPermission({"SECURITY_CREATE"})
    public ApiResponse<PolicyFeedImportResult> importPolicyFeed(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody PolicyFeedImportRequest request) {
        return ApiResponse.ok(feedImportService.importFromRequest(authUser, request));
    }
}
