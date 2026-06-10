package com.careertuner.analysis.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.analysis.dto.AnalysisSummaryResponse;
import com.careertuner.analysis.dto.CareerAnalysisRunResponse;
import com.careertuner.analysis.service.AnalysisService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/summary")
    public ApiResponse<AnalysisSummaryResponse> summary(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(analysisService.getSummary(authUser.id()));
    }

    // 사용자가 명시적으로 요청한 장기 경향 재분석. AI를 강제로 실행하고 크레딧을 차감한다.
    @PostMapping("/summary/refresh")
    public ApiResponse<AnalysisSummaryResponse> refresh(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(analysisService.refreshSummary(authUser.id()));
    }

    @GetMapping("/history")
    public ApiResponse<List<CareerAnalysisRunResponse>> history(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(analysisService.getHistory(authUser.id()));
    }
}
