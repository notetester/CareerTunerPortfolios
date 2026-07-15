package com.careertuner.ai.autoprep;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.ai.autoprep.dto.AutoPrepCancelRequest;
import com.careertuner.ai.autoprep.dto.AutoPrepIntakeResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepJobPostingCaseResponse;
import com.careertuner.ai.autoprep.dto.AutoPrepRequest;
import com.careertuner.ai.autoprep.dto.AutoPrepResponse;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AI 오케스트레이터 진입 API. 한 줄 요청 → 두뇌(Plan) → 6파트 순차 실행 → 결과.
 */
@RestController
@RequestMapping("/api/auto-prep")
@RequiredArgsConstructor
@RequiresConsent(ConsentType.AI_DATA)
public class AutoPrepController {

    private final AutoPrepOrchestrator orchestrator;
    private final AutoPrepIntakeService intakeService;
    private final AutoPrepAttachmentLoader attachmentLoader;
    private final AutoPrepCaseCreationService caseCreationService;

    /** 인테이크: 한 줄 요청을 해석해 슬롯을 확인한다(미리보기, 실행 X). ready=true 면 같은 요청으로 /run. */
    @PostMapping("/intake")
    public ApiResponse<AutoPrepIntakeResponse> intake(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AutoPrepRequest request) {
        attachmentLoader.validateRequestLimit(
                authUser.id(), request.jobPostingFileIds(), request.attachmentFileIds());
        return ApiResponse.ok(intakeService.intake(authUser.id(), request));
    }

    /** 실행: 두뇌가 세운 계획대로 6파트를 순차 실행한다. */
    @PostMapping("/run")
    @RequiresConsent(ConsentType.RESUME_ANALYSIS)
    public ApiResponse<AutoPrepResponse> run(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AutoPrepRequest request) {
        attachmentLoader.validateRequestLimit(
                authUser.id(), request.jobPostingFileIds(), request.attachmentFileIds());
        return ApiResponse.ok(orchestrator.run(authUser.id(), request));
    }

    /** 실행(SSE): 진행 과정을 실시간 스트리밍. 이벤트 = plan / part-start / substep / part-done / done. */
    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequiresConsent(ConsentType.RESUME_ANALYSIS)
    public SseEmitter runStream(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AutoPrepRequest request) {
        attachmentLoader.validateRequestLimit(
                authUser.id(), request.jobPostingFileIds(), request.attachmentFileIds());
        return orchestrator.runStream(authUser.id(), request);
    }

    /** 브라우저 abort보다 먼저/동시에 서버 협력적 취소 신호를 전달한다. */
    @PostMapping("/run/cancel")
    public ApiResponse<Void> cancelRun(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AutoPrepCancelRequest request) {
        orchestrator.cancel(authUser.id(), request.runId());
        return ApiResponse.ok();
    }

    /**
     * PDF/이미지 공고와 자소서 fileId를 지원 건 생성 전에 한 번에 플랜 검증한다. pendingFileId는 응답 유실
     * 재전송의 멱등키이며, 일반 applicationCaseId 실행은 이 경로를 타지 않아 기존 지원 건 사용을 방해하지 않는다.
     */
    @PostMapping(value = "/job-posting-case/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AutoPrepJobPostingCaseResponse> createJobPostingCase(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") String sourceType,
            @RequestParam("pendingFileId") Long pendingFileId,
            @RequestParam(required = false) List<Long> attachmentFileIds) {
        attachmentLoader.validateRequestLimit(authUser.id(), null, attachmentFileIds, 1);
        Long applicationCaseId = caseCreationService.createOrReuseUpload(
                authUser.id(), pendingFileId, file, sourceType);
        return ApiResponse.ok(new AutoPrepJobPostingCaseResponse(applicationCaseId));
    }
}
