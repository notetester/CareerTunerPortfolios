package com.careertuner.admin.jobanalysis.controller;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import java.util.List;
import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.common.dto.AdminMemoRequest;
import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisRow;
import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisSearchCriteria;
import com.careertuner.admin.jobanalysis.dto.AdminJobAnalysisSummary;
import com.careertuner.admin.jobanalysis.service.AdminJobAnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/job-analysis")
@RequireAdminPermission({"AI_READ"})
@RequiredArgsConstructor
public class AdminJobAnalysisController {

    private final AdminJobAnalysisService service;

    @GetMapping
    public ApiResponse<List<AdminJobAnalysisRow>> jobAnalyses(@AuthenticationPrincipal AuthUser authUser,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) String difficulty,
                                                              @RequestParam(required = false) Boolean confirmed,
                                                              @RequestParam(required = false) Boolean hasMemo,
                                                              @RequestParam(required = false) Long applicationCaseId,
                                                              @RequestParam(required = false) Long userId,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                                              @RequestParam(required = false) String sort,
                                                              @RequestParam(defaultValue = "50") int limit,
                                                              @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(service.jobAnalyses(authUser, AdminJobAnalysisSearchCriteria.builder()
                .keyword(keyword)
                .difficulty(difficulty)
                .confirmed(confirmed)
                .hasMemo(hasMemo)
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
    public ApiResponse<AdminJobAnalysisSummary> summary(@AuthenticationPrincipal AuthUser authUser,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String difficulty,
                                                        @RequestParam(required = false) Boolean confirmed,
                                                        @RequestParam(required = false) Boolean hasMemo,
                                                        @RequestParam(required = false) Long applicationCaseId,
                                                        @RequestParam(required = false) Long userId,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        return ApiResponse.ok(service.summary(authUser, AdminJobAnalysisSearchCriteria.builder()
                .keyword(keyword)
                .difficulty(difficulty)
                .confirmed(confirmed)
                .hasMemo(hasMemo)
                .applicationCaseId(applicationCaseId)
                .userId(userId)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .build()));
    }

    @PatchMapping("/{analysisId}/memo")
    @RequireAdminPermission({"AI_UPDATE"})
    public ApiResponse<Void> updateMemo(@AuthenticationPrincipal AuthUser authUser,
                                        @PathVariable Long analysisId,
                                        @Valid @RequestBody AdminMemoRequest request) {
        service.updateMemo(authUser, analysisId, request.adminMemo());
        return ApiResponse.ok();
    }
}
