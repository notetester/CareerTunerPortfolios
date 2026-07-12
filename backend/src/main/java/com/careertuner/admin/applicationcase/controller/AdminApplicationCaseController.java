package com.careertuner.admin.applicationcase.controller;

import java.util.List;
import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseDetail;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseRow;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSearchCriteria;
import com.careertuner.admin.applicationcase.dto.AdminApplicationCaseSummary;
import com.careertuner.admin.applicationcase.dto.AdminStatusUpdateRequest;
import com.careertuner.admin.applicationcase.service.AdminApplicationCaseService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/application-cases")
@RequireAdminPermission({"USER_READ"})
@RequiredArgsConstructor
public class AdminApplicationCaseController {

    private final AdminApplicationCaseService service;

    @GetMapping
    public ApiResponse<List<AdminApplicationCaseRow>> applicationCases(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "true") boolean includeArchived,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo,
            @RequestParam(required = false) String analysisState,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(service.applicationCases(authUser, AdminApplicationCaseSearchCriteria.builder()
                .keyword(keyword)
                .status(status)
                .includeArchived(includeArchived)
                .includeDeleted(includeDeleted)
                .sourceType(sourceType)
                .favorite(favorite)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .deadlineFrom(deadlineFrom)
                .deadlineTo(deadlineTo)
                .analysisState(analysisState)
                .sort(sort)
                .limit(limit)
                .offset(offset)
                .build()));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminApplicationCaseSummary> summary(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "true") boolean includeArchived,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadlineTo,
            @RequestParam(required = false) String analysisState) {
        return ApiResponse.ok(service.summary(authUser, AdminApplicationCaseSearchCriteria.builder()
                .keyword(keyword)
                .status(status)
                .includeArchived(includeArchived)
                .includeDeleted(includeDeleted)
                .sourceType(sourceType)
                .favorite(favorite)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .deadlineFrom(deadlineFrom)
                .deadlineTo(deadlineTo)
                .analysisState(analysisState)
                .build()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminApplicationCaseDetail> detail(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(service.detail(authUser, id));
    }

    @PatchMapping("/{id}/status")
    @RequireAdminPermission({"USER_UPDATE"})
    public ApiResponse<AdminApplicationCaseRow> updateStatus(@AuthenticationPrincipal AuthUser authUser,
                                                             @PathVariable Long id,
                                                             @Valid @RequestBody AdminStatusUpdateRequest request) {
        return ApiResponse.ok(service.updateStatus(authUser, id, request));
    }
}
