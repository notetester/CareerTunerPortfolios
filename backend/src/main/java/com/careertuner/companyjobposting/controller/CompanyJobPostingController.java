package com.careertuner.companyjobposting.controller;

import java.util.List;

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
import com.careertuner.companyjobposting.dto.CompanyJobPostingResponse;
import com.careertuner.companyjobposting.dto.JobPostingUpsertRequest;
import com.careertuner.companyjobposting.service.CompanyJobPostingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 기업 전용 "내 공고 관리" API. 권한은 company_profile 존재(=승인된 COMPANY)로 검증한다. */
@RestController
@RequestMapping("/api/company/job-postings")
@RequiredArgsConstructor
public class CompanyJobPostingController {

    private final CompanyJobPostingService jobPostingService;

    @GetMapping
    public ApiResponse<List<CompanyJobPostingResponse>> listMine(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(jobPostingService.listMine(authUser.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<CompanyJobPostingResponse> getMine(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(jobPostingService.getMine(authUser.id(), id));
    }

    @PostMapping
    public ApiResponse<CompanyJobPostingResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                         @Valid @RequestBody JobPostingUpsertRequest request) {
        return ApiResponse.ok(jobPostingService.create(authUser.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CompanyJobPostingResponse> update(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody JobPostingUpsertRequest request) {
        return ApiResponse.ok(jobPostingService.update(authUser.id(), id, request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<CompanyJobPostingResponse> close(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id) {
        return ApiResponse.ok(jobPostingService.close(authUser.id(), id));
    }
}
