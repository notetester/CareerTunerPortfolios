package com.careertuner.admin.companyanalysis.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisSearchCriteria;
import com.careertuner.admin.companyanalysis.dto.AdminCompanyAnalysisSummary;
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
                                                                      @RequestParam(required = false) String keyword,
                                                                      @RequestParam(required = false) String sourceType,
                                                                      @RequestParam(required = false) String industry,
                                                                      @RequestParam(required = false) Boolean confirmed,
                                                                      @RequestParam(required = false) Boolean hasMemo,
                                                                      @RequestParam(required = false) Boolean checked,
                                                                      @RequestParam(required = false) Boolean refreshDue,
                                                                      @RequestParam(required = false) Long applicationCaseId,
                                                                      @RequestParam(required = false) Long userId,
                                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                                                      @RequestParam(required = false) String sort,
                                                                      @RequestParam(defaultValue = "50") int limit,
                                                                      @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(service.companyAnalyses(authUser, AdminCompanyAnalysisSearchCriteria.builder()
                .keyword(keyword)
                .sourceType(sourceType)
                .industry(industry)
                .confirmed(confirmed)
                .hasMemo(hasMemo)
                .checked(checked)
                .refreshDue(refreshDue)
                .applicationCaseId(applicationCaseId)
                .userId(userId)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .sort(sort)
                .limit(limit)
                .offset(offset)
                .build()));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminCompanyAnalysisSummary> summary(@AuthenticationPrincipal AuthUser authUser,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String sourceType,
                                                            @RequestParam(required = false) String industry,
                                                            @RequestParam(required = false) Boolean confirmed,
                                                            @RequestParam(required = false) Boolean hasMemo,
                                                            @RequestParam(required = false) Boolean checked,
                                                            @RequestParam(required = false) Boolean refreshDue,
                                                            @RequestParam(required = false) Long applicationCaseId,
                                                            @RequestParam(required = false) Long userId,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        return ApiResponse.ok(service.summary(authUser, AdminCompanyAnalysisSearchCriteria.builder()
                .keyword(keyword)
                .sourceType(sourceType)
                .industry(industry)
                .confirmed(confirmed)
                .hasMemo(hasMemo)
                .checked(checked)
                .refreshDue(refreshDue)
                .applicationCaseId(applicationCaseId)
                .userId(userId)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .build()));
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
