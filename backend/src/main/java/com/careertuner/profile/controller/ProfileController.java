package com.careertuner.profile.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileAnalyzeResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.ProfileDocumentAnalyzeRequest;
import com.careertuner.profile.dto.ProfileDocumentImportRequest;
import com.careertuner.profile.dto.ProfileImportResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.service.ProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;

    @GetMapping
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.me(authUser));
    }

    @PutMapping
    public ApiResponse<UserProfileResponse> save(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestBody UserProfileRequest request) {
        return ApiResponse.ok(service.save(authUser, request));
    }

    /** 업로드된 파일 → resume_text / self_intro 동기 덤프. */
    @PostMapping("/import")
    public ApiResponse<ProfileImportResponse> importDocument(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody ProfileDocumentImportRequest request) {
        return ApiResponse.ok(service.importDocument(authUser, request));
    }

    /** 이력서 구조화 분석 비동기 발사 → 202 + jobId. */
    @PostMapping("/import/analyze")
    public ResponseEntity<ApiResponse<ProfileAnalyzeResponse>> startAnalyze(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody ProfileDocumentAnalyzeRequest request) {
        ProfileAnalyzeResponse body = service.startAnalyze(authUser, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(body));
    }

    /** 구조화 분석 작업 조회. */
    @GetMapping("/import/analyze/{jobId}")
    public ApiResponse<ProfileAnalyzeResponse> getAnalyze(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable String jobId) {
        return ApiResponse.ok(service.getAnalyze(authUser, jobId));
    }

    @PostMapping("/ai/summary")
    public ApiResponse<ProfileAiResponse> summarize(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.summarize(authUser));
    }

    @PostMapping("/ai/skills")
    public ApiResponse<ProfileAiResponse> extractSkills(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.extractSkills(authUser));
    }

    @PostMapping("/ai/completeness")
    public ApiResponse<ProfileCompletenessResponse> diagnoseCompleteness(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.diagnoseCompleteness(authUser));
    }
}
