package com.careertuner.fitanalysis.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.service.FitAnalysisService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/fit-analyses")
@RequiredArgsConstructor
public class FitAnalysisController {

    private final FitAnalysisService fitAnalysisService;

    @GetMapping
    public ApiResponse<List<FitAnalysisDetailResponse>> list(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(fitAnalysisService.list(authUser.id()));
    }

    @GetMapping("/application-cases/{applicationCaseId}")
    public ApiResponse<FitAnalysisDetailResponse> getByApplicationCase(@AuthenticationPrincipal AuthUser authUser,
                                                                       @PathVariable Long applicationCaseId) {
        return ApiResponse.ok(fitAnalysisService.getByApplicationCase(authUser.id(), applicationCaseId));
    }

    /**
     * 적합도 분석 생성/재생성(C 담당 AI 12~15). 공고 분석 결과와 프로필을 비교한다.
     * 현재 mock 동작, API 키 주입 시 동일 엔드포인트로 실 분석이 동작한다.
     */
    @PostMapping("/application-cases/{applicationCaseId}")
    public ApiResponse<FitAnalysisDetailResponse> generate(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long applicationCaseId) {
        return ApiResponse.ok(fitAnalysisService.generate(authUser.id(), applicationCaseId));
    }
}
