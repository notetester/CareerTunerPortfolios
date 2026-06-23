package com.careertuner.ai.autoprep;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
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
    private final AutoPrepIntakeService intakeService;

    /** 인테이크: 한 줄 요청을 해석해 슬롯을 확인한다(미리보기, 실행 X). ready=true 면 같은 요청으로 /run. */
    @PostMapping("/intake")
    public ApiResponse<AutoPrepIntakeResponse> intake(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AutoPrepRequest request) {
        return ApiResponse.ok(intakeService.intake(authUser.id(), request));
    }

    /** 실행: 두뇌가 세운 계획대로 6파트를 순차 실행한다. */
    @PostMapping("/run")
    public ApiResponse<AutoPrepResponse> run(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AutoPrepRequest request) {
        return ApiResponse.ok(orchestrator.run(authUser.id(), request));
    }

    /** 실행(SSE): 진행 과정을 실시간 스트리밍. 이벤트 = plan / part-start / substep / part-done / done. */
    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody AutoPrepRequest request) {
        return orchestrator.runStream(authUser.id(), request);
    }
}
