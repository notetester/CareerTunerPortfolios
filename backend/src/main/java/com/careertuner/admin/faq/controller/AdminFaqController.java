package com.careertuner.admin.faq.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.faq.dto.AdminFaqRequest;
import com.careertuner.admin.faq.dto.AdminFaqResponse;
import com.careertuner.admin.faq.service.AdminFaqService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/faq")
@RequireAdminPermission({"CONTENT_READ"})
@RequiredArgsConstructor
public class AdminFaqController {

    private final AdminFaqService faqService;

    @GetMapping
    public ApiResponse<List<AdminFaqResponse>> getFaqs(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(faqService.getFaqs(authUser));
    }

    @PostMapping
    @RequireAdminPermission({"CONTENT_CREATE"})
    public ApiResponse<AdminFaqResponse> createFaq(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AdminFaqRequest request) {
        return ApiResponse.ok(faqService.createFaq(authUser, request));
    }

    @PutMapping("/{id}")
    @RequireAdminPermission({"CONTENT_UPDATE"})
    public ApiResponse<AdminFaqResponse> updateFaq(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody AdminFaqRequest request) {
        return ApiResponse.ok(faqService.updateFaq(authUser, id, request));
    }

    @DeleteMapping("/{id}")
    @RequireAdminPermission({"CONTENT_DELETE"})
    public ApiResponse<Void> deleteFaq(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        faqService.deleteFaq(authUser, id);
        return ApiResponse.ok();
    }
}
