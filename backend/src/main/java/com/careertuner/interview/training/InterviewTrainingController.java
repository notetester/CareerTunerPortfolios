package com.careertuner.interview.training;

import com.careertuner.admin.permission.annotation.RequireAdminPermission;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.interview.training.dto.EvalHarnessResponse;
import com.careertuner.interview.training.dto.FineTuneResponse;
import com.careertuner.interview.training.dto.TrainingStatsResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 면접 학습 데이터 파이프라인: 통계 / JSONL 추출 / 평가 하니스 / 파인튜닝. */
@RestController
@RequestMapping("/api/admin/interview/training")
@RequireAdminPermission({"AI_READ"})
@RequiredArgsConstructor
public class InterviewTrainingController {

    private final InterviewTrainingService service;

    @GetMapping("/stats")
    public ApiResponse<TrainingStatsResponse> stats(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.stats(authUser));
    }

    /** JSONL 다운로드(파인튜닝 입력 포맷). */
    @GetMapping("/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal AuthUser authUser,
                                         @RequestParam(defaultValue = "1000") int limit) {
        String jsonl = service.exportJsonl(authUser, limit);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"interview-training.jsonl\"")
                .body(jsonl);
    }

    @PostMapping("/eval")
    @RequireAdminPermission({"AI_CREATE"})
    public ApiResponse<EvalHarnessResponse> eval(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestParam(defaultValue = "20") int sampleSize) {
        return ApiResponse.ok(service.runEvalHarness(authUser, sampleSize));
    }

    @PostMapping("/fine-tune")
    @RequireAdminPermission({"AI_CREATE"})
    public ApiResponse<FineTuneResponse> fineTune(@AuthenticationPrincipal AuthUser authUser,
                                                  @RequestParam(required = false) String baseModel) {
        return ApiResponse.ok(service.startFineTune(authUser, baseModel));
    }
}
