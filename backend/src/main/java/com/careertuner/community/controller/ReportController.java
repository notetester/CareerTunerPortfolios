package com.careertuner.community.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.community.dto.CreateReportRequest;
import com.careertuner.community.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/community/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResponse<Void> createReport(
            @Validated @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        reportService.createReport(request, authUser.id());
        return ApiResponse.ok();
    }
}
