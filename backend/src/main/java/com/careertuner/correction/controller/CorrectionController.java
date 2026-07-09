package com.careertuner.correction.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.dto.CorrectionResponse;
import com.careertuner.correction.dto.CorrectionWarmupResponse;
import com.careertuner.correction.ai.CorrectionModelWarmupService;
import com.careertuner.correction.service.CorrectionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/corrections")
@RequiredArgsConstructor
public class CorrectionController {

    private final CorrectionService correctionService;
    private final CorrectionModelWarmupService warmupService;

    @PostMapping("/warmup")
    public ApiResponse<CorrectionWarmupResponse> warmup(@AuthenticationPrincipal AuthUser authUser) {
        authUser.id();
        return ApiResponse.ok(warmupService.warmAsync("CORRECTION_PAGE"));
    }

    @PostMapping
    public ApiResponse<CorrectionResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                  @Valid @RequestBody CorrectionCreateRequest request) {
        return ApiResponse.ok(correctionService.create(authUser.id(), request));
    }

    @GetMapping
    public ApiResponse<List<CorrectionResponse>> list(@AuthenticationPrincipal AuthUser authUser,
                                                      @RequestParam(required = false) Long applicationCaseId,
                                                      @RequestParam(required = false) String correctionType,
                                                      @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(correctionService.list(authUser.id(), applicationCaseId, correctionType, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<CorrectionResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long id) {
        return ApiResponse.ok(correctionService.get(authUser.id(), id));
    }
}
