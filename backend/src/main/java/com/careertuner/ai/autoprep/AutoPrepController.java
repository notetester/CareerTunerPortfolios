package com.careertuner.ai.autoprep;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.autoprep.dto.AutoPrepResponse;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * AI 오케스트레이터 진입 API. 한 줄 요청 → 두뇌(Plan) → 6파트 순차 실행 → 결과.
 */
@RestController
@RequestMapping("/api/auto-prep")
@RequiredArgsConstructor
public class AutoPrepController {

    private final AutoPrepOrchestrator orchestrator;

    @PostMapping("/run")
    public ApiResponse<AutoPrepResponse> run(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AutoPrepRequest request) {
        return ApiResponse.ok(orchestrator.run(authUser.id(), request));
    }
}
