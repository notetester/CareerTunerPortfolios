package com.careertuner.billing.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.billing.dto.AiChargePreviewRequest;
import com.careertuner.billing.dto.AiChargePreviewResponse;
import com.careertuner.billing.service.AiChargePreviewService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/billing/charge-preview")
@RequiredArgsConstructor
public class AiChargePreviewController {

    private final AiChargePreviewService previewService;

    @PostMapping
    public ApiResponse<AiChargePreviewResponse> preview(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AiChargePreviewRequest request) {
        return ApiResponse.ok(previewService.preview(authUser.id(), request));
    }
}
