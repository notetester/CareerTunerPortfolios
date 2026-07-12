package com.careertuner.profile.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;
import com.careertuner.file.dto.FileAssetResponse;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileAnalyzeResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.ProfileDocumentAnalyzeRequest;
import com.careertuner.profile.dto.ProfileDocumentImportRequest;
import com.careertuner.profile.dto.ProfileImportResponse;
import com.careertuner.profile.dto.PortfolioFileLinkRequest;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.service.ProfileService;
import com.careertuner.profile.service.ProfilePortfolioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;
    private final ProfilePortfolioService portfolioService;

    @GetMapping
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.me(authUser));
    }

    @PutMapping
    public ApiResponse<UserProfileResponse> save(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestBody UserProfileRequest request) {
        return ApiResponse.ok(service.save(authUser, request));
    }

    /** 포트폴리오를 업로드와 동시에 현재 프로필에 연결한다. */
    @PostMapping(value = "/portfolio-files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileAssetResponse> uploadPortfolio(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(portfolioService.upload(authUser.id(), file));
    }

    /** 기존 미연결 PORTFOLIO 파일을 현재 프로필로 입양한다(재시도·구형 클라이언트 호환). */
    @PostMapping("/portfolio-files/link")
    public ApiResponse<List<FileAssetResponse>> linkPortfolioFiles(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody PortfolioFileLinkRequest request) {
        return ApiResponse.ok(portfolioService.link(authUser.id(), request == null ? null : request.fileIds()));
    }

    /** 현재 프로필에 연결된 포트폴리오 파일 메타 목록. */
    @GetMapping("/portfolio-files")
    public ApiResponse<List<FileAssetResponse>> portfolioFiles(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(portfolioService.list(authUser.id()));
    }

    /** 현재 프로필에 연결된 포트폴리오 파일을 실제 저장소까지 삭제한다. */
    @DeleteMapping("/portfolio-files/{fileId}")
    public ApiResponse<Void> deletePortfolioFile(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long fileId) {
        portfolioService.delete(authUser.id(), fileId);
        return ApiResponse.ok();
    }

    /** 업로드된 파일 → resume_text / self_intro 동기 덤프. */
    @PostMapping("/import")
    @RequiresConsent(ConsentType.RESUME_ANALYSIS)
    public ApiResponse<ProfileImportResponse> importDocument(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody ProfileDocumentImportRequest request) {
        return ApiResponse.ok(service.importDocument(authUser, request));
    }

    /** 이력서 구조화 분석 비동기 발사 → 202 + jobId. */
    @PostMapping("/import/analyze")
    @RequiresConsent({ConsentType.AI_DATA, ConsentType.RESUME_ANALYSIS})
    public ResponseEntity<ApiResponse<ProfileAnalyzeResponse>> startAnalyze(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody ProfileDocumentAnalyzeRequest request) {
        ProfileAnalyzeResponse body = service.startAnalyze(authUser, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(body));
    }

    /** 구조화 분석 작업 조회. */
    @GetMapping("/import/analyze/{jobId}")
    @RequiresConsent({ConsentType.AI_DATA, ConsentType.RESUME_ANALYSIS})
    public ApiResponse<ProfileAnalyzeResponse> getAnalyze(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable String jobId) {
        return ApiResponse.ok(service.getAnalyze(authUser, jobId));
    }

    @PostMapping("/ai/summary")
    @RequiresConsent({ConsentType.AI_DATA, ConsentType.RESUME_ANALYSIS})
    public ApiResponse<ProfileAiResponse> summarize(@AuthenticationPrincipal AuthUser authUser,
                                                    @RequestParam(required = false) String model) {
        return ApiResponse.ok(service.summarize(authUser, RequestedAiModel.parse(model)));
    }

    @PostMapping("/ai/skills")
    @RequiresConsent({ConsentType.AI_DATA, ConsentType.RESUME_ANALYSIS})
    public ApiResponse<ProfileAiResponse> extractSkills(@AuthenticationPrincipal AuthUser authUser,
                                                        @RequestParam(required = false) String model) {
        return ApiResponse.ok(service.extractSkills(authUser, RequestedAiModel.parse(model)));
    }

    @PostMapping("/ai/completeness")
    @RequiresConsent({ConsentType.AI_DATA, ConsentType.RESUME_ANALYSIS})
    public ApiResponse<ProfileCompletenessResponse> diagnoseCompleteness(@AuthenticationPrincipal AuthUser authUser,
                                                                         @RequestParam(required = false) String model) {
        return ApiResponse.ok(service.diagnoseCompleteness(authUser, RequestedAiModel.parse(model)));
    }

    /** 저장된 프로필 AI 분석 조회 — 새로고침 후에도 최근 분석 결과를 보여준다(조회는 동의 불요, 저장분 읽기만). */
    @GetMapping("/ai-analysis")
    public ApiResponse<com.careertuner.profile.dto.ProfileAiAnalysisResponse> aiAnalysis(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.aiAnalysis(authUser));
    }
}
