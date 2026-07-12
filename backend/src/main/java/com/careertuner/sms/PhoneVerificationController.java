package com.careertuner.sms;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.sms.PhoneVerificationService.OtpRequestResult;
import com.careertuner.sms.PhoneVerificationService.OtpVerifyResult;
import com.careertuner.sms.PhoneVerificationService.PhoneAuthConfigResult;
import com.careertuner.sms.PhoneVerificationService.PhoneStatusResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 전화번호 SMS OTP 인증 API. 로그인 사용자 대상(기본 authenticated).
 * 프런트 프록시 기준 모든 경로는 /api/auth/phone/** 하위.
 */
@RestController
@RequestMapping("/api/auth/phone")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    /** 인증번호 발송. Mock 모드면 응답에 devCode 가 포함되어 데모에서 자동입력할 수 있다. */
    @PostMapping("/request-otp")
    public ApiResponse<OtpRequestResult> requestOtp(@AuthenticationPrincipal AuthUser authUser,
                                                    @Valid @RequestBody OtpRequestBody body) {
        return ApiResponse.ok(phoneVerificationService.requestOtp(authUser.id(), body.phone()));
    }

    /** 인증번호 검증. 성공 시 전화번호 인증 완료 처리. */
    @PostMapping("/verify-otp")
    public ApiResponse<OtpVerifyResult> verifyOtp(@AuthenticationPrincipal AuthUser authUser,
                                                  @Valid @RequestBody OtpVerifyBody body) {
        return ApiResponse.ok(phoneVerificationService.verifyOtp(authUser.id(), body.phone(), body.code()));
    }

    /** 현재 전화번호 인증 상태. */
    @GetMapping("/status")
    public ApiResponse<PhoneStatusResult> status(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(phoneVerificationService.status(authUser.id()));
    }

    /**
     * 전화번호 인증 흐름 설정. provider="firebase" 이고 웹 config 완비면 Firebase 웹 인증 흐름을,
     * 그 외에는 백엔드 OTP 흐름을 프런트가 선택한다. 서비스계정 등 비밀값은 내려보내지 않는다.
     */
    @GetMapping("/config")
    public ApiResponse<PhoneAuthConfigResult> config() {
        return ApiResponse.ok(phoneVerificationService.authConfig());
    }

    /** Firebase Phone Auth ID 토큰 검증. 성공 시 전화번호 인증 완료 처리. */
    @PostMapping("/verify-firebase")
    public ApiResponse<OtpVerifyResult> verifyFirebase(@AuthenticationPrincipal AuthUser authUser,
                                                       @Valid @RequestBody VerifyFirebaseBody body) {
        return ApiResponse.ok(phoneVerificationService.verifyFirebase(authUser.id(), body.idToken()));
    }

    public record OtpRequestBody(@NotBlank String phone) {
    }

    public record OtpVerifyBody(@NotBlank String phone, @NotBlank String code) {
    }

    public record VerifyFirebaseBody(@NotBlank String idToken) {
    }
}
