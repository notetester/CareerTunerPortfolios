package com.careertuner.admin.securityops.controller;

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

import com.careertuner.admin.securityops.dto.SecurityAppealDecisionRequest;
import com.careertuner.admin.securityops.dto.SecurityAppealPolicyRequest;
import com.careertuner.admin.securityops.dto.SecurityAppealPolicyRow;
import com.careertuner.admin.securityops.dto.SecurityAppealRow;
import com.careertuner.admin.securityops.dto.SecurityBlockRuleRequest;
import com.careertuner.admin.securityops.dto.SecurityBlockRuleRow;
import com.careertuner.admin.securityops.dto.SecurityOpsSummaryResponse;
import com.careertuner.admin.securityops.dto.SecurityProviderConfigRequest;
import com.careertuner.admin.securityops.dto.SecurityProviderConfigRow;
import com.careertuner.admin.securityops.dto.SecurityProviderHealthHistoryRow;
import com.careertuner.admin.securityops.dto.SecurityReviewRequest;
import com.careertuner.admin.securityops.dto.SecurityReviewRow;
import com.careertuner.admin.securityops.dto.WafSyncEventRow;
import com.careertuner.admin.securityops.service.AdminSecurityOpsService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
public class AdminSecurityOpsController {

    private final AdminSecurityOpsService service;

    @GetMapping("/summary")
    public ApiResponse<SecurityOpsSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.summary(authUser));
    }

    /** 런타임 차단 캐시를 DB 와 수동 재동기화. */
    @PostMapping("/block-cache/sync")
    public ApiResponse<AdminSecurityOpsService.BlockCacheStatus> syncBlockCache(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.syncBlockCache(authUser));
    }

    /** 현재 차단 캐시 상태(출처·규칙 수·적재 시각). */
    @GetMapping("/block-cache/status")
    public ApiResponse<AdminSecurityOpsService.BlockCacheStatus> blockCacheStatus(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.blockCacheStatus(authUser));
    }

    /** WAF 동기화 큐를 즉시 처리(수동 드레인). 처리 건수를 반환. */
    @PostMapping("/waf-sync/process")
    public ApiResponse<Integer> processWafSync(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.processWafSyncNow(authUser));
    }

    @GetMapping("/block-rules")
    public ApiResponse<List<SecurityBlockRuleRow>> blockRules(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(service.blockRules(authUser, keyword, ruleType, active, limit));
    }

    @PostMapping("/block-rules")
    public ApiResponse<SecurityBlockRuleRow> createBlockRule(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SecurityBlockRuleRequest request) {
        return ApiResponse.ok(service.createBlockRule(authUser, request));
    }

    @PatchMapping("/block-rules/{id}")
    public ApiResponse<SecurityBlockRuleRow> updateBlockRule(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody SecurityBlockRuleRequest request) {
        return ApiResponse.ok(service.updateBlockRule(authUser, id, request));
    }

    @PostMapping("/block-rules/{id}/waf-sync")
    public ApiResponse<SecurityBlockRuleRow> queueWafSync(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestParam(defaultValue = "UPSERT") String operationType) {
        return ApiResponse.ok(service.queueWafSync(authUser, id, operationType));
    }

    @GetMapping("/waf-events")
    public ApiResponse<List<WafSyncEventRow>> wafEvents(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(service.wafEvents(authUser, keyword, status, limit));
    }

    @GetMapping("/providers")
    public ApiResponse<List<SecurityProviderConfigRow>> providers(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String providerType) {
        return ApiResponse.ok(service.providers(authUser, keyword, providerType));
    }

    @GetMapping("/provider-health-history")
    public ApiResponse<List<SecurityProviderHealthHistoryRow>> providerHealthHistory(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String statusAfter,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(service.providerHealthHistory(authUser, keyword, statusAfter, limit));
    }

    @PatchMapping("/providers/{providerCode}")
    public ApiResponse<SecurityProviderConfigRow> updateProvider(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable String providerCode,
            @Valid @RequestBody SecurityProviderConfigRequest request) {
        return ApiResponse.ok(service.updateProvider(authUser, providerCode, request));
    }

    @PostMapping("/providers/{providerCode}/health-check")
    public ApiResponse<SecurityProviderConfigRow> healthCheck(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable String providerCode) {
        return ApiResponse.ok(service.runProviderHealthCheck(authUser, providerCode));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<SecurityReviewRow>> reviews(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reviewType,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(service.reviews(authUser, keyword, status, reviewType, limit));
    }

    @PostMapping("/reviews")
    public ApiResponse<SecurityReviewRow> createReview(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SecurityReviewRequest request) {
        return ApiResponse.ok(service.createReview(authUser, request));
    }

    @PatchMapping("/reviews/{id}")
    public ApiResponse<SecurityReviewRow> updateReview(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody SecurityReviewRequest request) {
        return ApiResponse.ok(service.updateReview(authUser, id, request));
    }

    @GetMapping("/appeal-policy")
    public ApiResponse<SecurityAppealPolicyRow> appealPolicy(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.appealPolicy(authUser));
    }

    @PatchMapping("/appeal-policy")
    public ApiResponse<SecurityAppealPolicyRow> updateAppealPolicy(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SecurityAppealPolicyRequest request) {
        return ApiResponse.ok(service.updateAppealPolicy(authUser, request));
    }

    @GetMapping("/appeals")
    public ApiResponse<List<SecurityAppealRow>> appeals(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(service.appeals(authUser, keyword, status, limit));
    }

    @PatchMapping("/appeals/{id}")
    public ApiResponse<SecurityAppealRow> decideAppeal(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody SecurityAppealDecisionRequest request) {
        return ApiResponse.ok(service.decideAppeal(authUser, id, request));
    }
}
