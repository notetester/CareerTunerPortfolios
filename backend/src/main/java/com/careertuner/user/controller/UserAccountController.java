package com.careertuner.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.auth.service.AuthService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.user.dto.AccountInfoResponse;
import com.careertuner.user.dto.EmailRegistrationRequest;
import com.careertuner.user.dto.LoginIdRequest;
import com.careertuner.user.dto.PhoneRequest;
import com.careertuner.user.dto.SocialLinkUrlResponse;
import com.careertuner.user.service.UserAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 계정 확충 API — 로그인 아이디·전화번호 설정, 연결 계정 조회. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService service;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<AccountInfoResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(service.accountInfo(authUser.id()));
    }

    @PostMapping("/login-id")
    public ApiResponse<AccountInfoResponse> setLoginId(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody LoginIdRequest request) {
        return ApiResponse.ok(service.setLoginId(authUser.id(), request.loginId()));
    }

    @PostMapping("/phone")
    public ApiResponse<AccountInfoResponse> setPhone(@AuthenticationPrincipal AuthUser authUser,
                                                     @Valid @RequestBody PhoneRequest request) {
        return ApiResponse.ok(service.setPhone(authUser.id(), request.phone()));
    }

    @PostMapping("/email-registration")
    public ApiResponse<Void> requestEmailRegistration(@AuthenticationPrincipal AuthUser authUser,
                                                      @Valid @RequestBody EmailRegistrationRequest request) {
        service.requestEmailRegistration(authUser.id(), request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/social/{provider}/link-url")
    public ApiResponse<SocialLinkUrlResponse> socialLinkUrl(@AuthenticationPrincipal AuthUser authUser,
                                                            @PathVariable String provider) {
        return ApiResponse.ok(new SocialLinkUrlResponse(authService.buildSocialLinkUrl(authUser.id(), provider)));
    }

    @DeleteMapping("/social/{provider}")
    public ApiResponse<AccountInfoResponse> unlinkSocial(@AuthenticationPrincipal AuthUser authUser,
                                                         @PathVariable String provider) {
        return ApiResponse.ok(service.unlinkSocial(authUser.id(), provider));
    }
}
