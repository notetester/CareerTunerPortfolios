package com.careertuner.fitanalysis.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisDetailResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisHistoryEntryResponse;
import com.careertuner.fitanalysis.dto.FitAnalysisLearningTaskResponse;
import com.careertuner.fitanalysis.dto.UpdateLearningTaskRequest;
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
     * 재분석 히스토리(최신순). 직전 분석 대비 점수 변화와 매칭/부족 역량 변화를 제공한다.
     */
    @GetMapping("/application-cases/{applicationCaseId}/history")
    public ApiResponse<List<FitAnalysisHistoryEntryResponse>> history(@AuthenticationPrincipal AuthUser authUser,
                                                                      @PathVariable Long applicationCaseId) {
        return ApiResponse.ok(fitAnalysisService.getHistory(authUser.id(), applicationCaseId));
    }

    /**
     * 적합도 분석 생성/재생성(C 담당 AI 12~15). 공고 분석 결과와 프로필을 비교한다.
     * API 키가 없으면 mock, 있으면 동일 엔드포인트로 실제 구조화 분석이 동작한다.
     */
    @PostMapping("/application-cases/{applicationCaseId}")
    public ApiResponse<FitAnalysisDetailResponse> generate(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long applicationCaseId,
                                                           @RequestParam(defaultValue = "false") boolean certificateStrategy) {
        return ApiResponse.ok(fitAnalysisService.generate(authUser.id(), applicationCaseId, certificateStrategy));
    }

    @PatchMapping("/{fitAnalysisId}/learning-tasks/{taskId}")
    public ApiResponse<FitAnalysisLearningTaskResponse> updateLearningTask(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long fitAnalysisId,
            @PathVariable Long taskId,
            @RequestBody UpdateLearningTaskRequest request) {
        return ApiResponse.ok(fitAnalysisService.updateLearningTask(
                authUser.id(),
                fitAnalysisId,
                taskId,
                request.completed()));
    }
}
