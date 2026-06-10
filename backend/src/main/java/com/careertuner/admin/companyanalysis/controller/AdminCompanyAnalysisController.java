package com.careertuner.admin.companyanalysis.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.dto.AdminMemoRequest;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisMetadataRequest;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisRow;
import com.careertuner.admin.companyanalysis.service.AdminCompanyAnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/company-analysis")
@RequiredArgsConstructor
public class AdminCompanyAnalysisController {

    private final AdminCompanyAnalysisService service;

    @GetMapping
    public ApiResponse<List<AdminCompanyAnalysisRow>> companyAnalyses(@AuthenticationPrincipal AuthUser authUser,
                                                                      @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.companyAnalyses(authUser, limit));
    }

    @PatchMapping("/{analysisId}/memo")
    public ApiResponse<Void> updateMemo(@AuthenticationPrincipal AuthUser authUser,
                                        @PathVariable Long analysisId,
                                        @Valid @RequestBody AdminMemoRequest request) {
        service.updateMemo(authUser, analysisId, request.adminMemo());
        return ApiResponse.ok();
    }

    @PatchMapping("/{analysisId}/metadata")
    public ApiResponse<Void> updateMetadata(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long analysisId,
                                            @Valid @RequestBody AdminCompanyAnalysisMetadataRequest request) {
        service.updateMetadata(authUser, analysisId, request);
        return ApiResponse.ok();
    }
}
