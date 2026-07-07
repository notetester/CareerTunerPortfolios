package com.careertuner.admin.company.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.company.dto.AdminRejectRequest;
import com.careertuner.admin.common.AdminAccess;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.companyjobposting.dto.JobPostingReviewDetailResponse;
import com.careertuner.companyjobposting.dto.JobPostingReviewRow;
import com.careertuner.companyjobposting.service.CompanyJobPostingService;

import lombok.RequiredArgsConstructor;

/** 채용공고 검토 큐 콘솔 API — 신규 등록(PENDING_REVIEW)과 수정 변경본(revision PENDING)을 함께 다룬다. */
@RestController
@RequestMapping("/api/admin/company/job-postings")
@RequiredArgsConstructor
public class AdminJobPostingReviewController {

    private final CompanyJobPostingService jobPostingService;

    @GetMapping
    public ApiResponse<List<JobPostingReviewRow>> queue(@AuthenticationPrincipal AuthUser authUser) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(jobPostingService.reviewQueue());
    }

    /** 검토 상세 — 현재 게시본 + 대기 변경본(수정 검토면 프런트가 필드별 diff 를 그린다). */
    @GetMapping("/{id}")
    public ApiResponse<JobPostingReviewDetailResponse> detail(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long id) {
        AdminAccess.requireAdmin(authUser);
        return ApiResponse.ok(jobPostingService.reviewDetail(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approve(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
        AdminAccess.requireAdmin(authUser);
        jobPostingService.approveReview(authUser.id(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id,
                                    @RequestBody AdminRejectRequest request) {
        AdminAccess.requireAdmin(authUser);
        jobPostingService.rejectReview(authUser.id(), id, request.reason());
        return ApiResponse.ok();
    }
}
