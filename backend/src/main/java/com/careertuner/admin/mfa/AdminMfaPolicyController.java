package com.careertuner.admin.mfa;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.auth.dto.MfaPolicyResponse;
import com.careertuner.auth.dto.MfaPolicyUpdateRequest;
import com.careertuner.auth.service.MfaService;
import com.careertuner.admin.permission.annotation.RequireAdminPermission;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/mfa-policy")
@RequireAdminPermission({"POLICY_MANAGE", "POLICY_ADMIN"})
@RequiredArgsConstructor
public class AdminMfaPolicyController {
    private final MfaService mfaService;

    @GetMapping
    public ApiResponse<MfaPolicyResponse> getPolicy() {
        return ApiResponse.ok(mfaService.policyResponse());
    }

    @PutMapping
    public ApiResponse<MfaPolicyResponse> updatePolicy(@AuthenticationPrincipal AuthUser authUser,
                                                       @RequestBody MfaPolicyUpdateRequest request) {
        return ApiResponse.ok(mfaService.updatePolicy(authUser, request));
    }
}
