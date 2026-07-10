package com.careertuner.companyjobposting.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.companyjobposting.dto.CompanyJobPostingResponse;
import com.careertuner.companyjobposting.dto.JobBoardAnalyzeResponse;
import com.careertuner.companyjobposting.dto.JobPostingPageResponse;
import com.careertuner.companyjobposting.service.CompanyJobPostingService;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;

import lombok.RequiredArgsConstructor;

/**
 * 채용공고 게시판 조회 API — PUBLISHED 공고만 노출한다.
 * <p>비로그인 공개(permitAll)는 SecurityConfig(공통 영역) 변경이 필요해 이번 범위에서 제외 —
 * 현재는 로그인 사용자 전체가 열람 가능하다.
 */
@RestController
@RequestMapping("/api/job-board")
@RequiredArgsConstructor
public class JobBoardController {

    private final CompanyJobPostingService jobPostingService;

    @GetMapping
    public ApiResponse<JobPostingPageResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String jobRole,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String careerLevel,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.ok(jobPostingService.search(
                keyword, jobRole, location, employmentType, careerLevel, sort, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<CompanyJobPostingResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(jobPostingService.getPublished(id));
    }

    /** "이 공고로 분석하기" — 공고 본문 텍스트로 지원 건을 만들고 caseId 를 반환한다. */
    @PostMapping("/{id}/analyze")
    @RequiresConsent(ConsentType.AI_DATA)
    public ApiResponse<JobBoardAnalyzeResponse> analyze(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id) {
        return ApiResponse.ok(jobPostingService.analyze(authUser.id(), id));
    }
}
