package com.careertuner.admin.interview.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.interview.dto.AdminInterviewSessionDetail;
import com.careertuner.admin.interview.dto.AdminInterviewSessionRow;
import com.careertuner.admin.interview.service.AdminInterviewService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/interview")
@RequiredArgsConstructor
public class AdminInterviewController {

    private final AdminInterviewService service;

    @GetMapping("/sessions")
    public ApiResponse<List<AdminInterviewSessionRow>> sessions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.sessions(authUser, keyword, mode, limit));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<AdminInterviewSessionDetail> detail(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long id) {
        return ApiResponse.ok(service.detail(authUser, id));
    }
}
