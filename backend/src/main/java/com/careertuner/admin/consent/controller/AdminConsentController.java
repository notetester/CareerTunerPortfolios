package com.careertuner.admin.consent.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.dto.ConsentView;
import com.careertuner.consent.service.ConsentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/consents")
@RequiredArgsConstructor
public class AdminConsentController {

    private final ConsentService service;

    @GetMapping
    public ApiResponse<List<ConsentView>> consents(@AuthenticationPrincipal AuthUser authUser,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String consentType,
                                                   @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.adminConsents(authUser, keyword, consentType, limit));
    }
}
