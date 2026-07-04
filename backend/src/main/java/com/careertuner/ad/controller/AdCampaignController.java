package com.careertuner.ad.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.ad.dto.AdCampaignRequest;
import com.careertuner.ad.dto.AdCampaignResponse;
import com.careertuner.ad.dto.AdEventRequest;
import com.careertuner.ad.service.AdCampaignService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Validated
public class AdCampaignController {

    private final AdCampaignService adService;

    @GetMapping("/api/ads")
    public ApiResponse<List<AdCampaignResponse>> ads(@AuthenticationPrincipal AuthUser authUser,
                                                     @RequestParam(defaultValue = "WEB") String surface) {
        return ApiResponse.ok(adService.visibleAds(authUser == null ? null : authUser.id(), surface));
    }

    @PostMapping("/api/ads/{campaignId}/events")
    public ApiResponse<Void> event(@AuthenticationPrincipal AuthUser authUser,
                                   @PathVariable Long campaignId,
                                   @RequestBody(required = false) AdEventRequest request) {
        adService.recordEvent(authUser == null ? null : authUser.id(), campaignId,
                request == null ? "WEB" : request.surface(),
                request == null ? "IMPRESSION" : request.eventType());
        return ApiResponse.ok();
    }

    @GetMapping("/api/admin/ads")
    public ApiResponse<List<AdCampaignResponse>> adminList(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String surface,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return ApiResponse.ok(adService.adminList(authUser, surface, active, keyword, limit));
    }

    @PostMapping("/api/admin/ads")
    public ApiResponse<AdCampaignResponse> adminCreate(@AuthenticationPrincipal AuthUser authUser,
                                                       @RequestBody AdCampaignRequest request) {
        return ApiResponse.ok(adService.adminCreate(authUser, request));
    }

    @PatchMapping("/api/admin/ads/{id}")
    public ApiResponse<AdCampaignResponse> adminUpdate(@AuthenticationPrincipal AuthUser authUser,
                                                       @PathVariable Long id,
                                                       @RequestBody AdCampaignRequest request) {
        return ApiResponse.ok(adService.adminUpdate(authUser, id, request));
    }
}
