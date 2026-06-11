package com.careertuner.consent.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.dto.ConsentRequest;
import com.careertuner.consent.dto.ConsentStatusResponse;
import com.careertuner.consent.service.ConsentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService service;

    @GetMapping("/me")
    public ApiResponse<ConsentStatusResponse> status(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.status(authUser));
    }

    @PostMapping("/me")
    public ApiResponse<ConsentStatusResponse> save(@AuthenticationPrincipal AuthUser authUser,
                                                   @RequestBody ConsentRequest request) {
        return ApiResponse.ok(service.save(authUser, request, "USER"));
    }

    @PostMapping("/ai/revoke")
    public ApiResponse<ConsentStatusResponse> revokeAi(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.revokeAi(authUser));
    }
}
