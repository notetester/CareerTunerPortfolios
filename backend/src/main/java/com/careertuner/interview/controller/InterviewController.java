package com.careertuner.interview.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.billing.policy.RequiresAiCharge;
import com.careertuner.billing.service.AiChargeRequestSettlementService;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;
import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.DispatchInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateFollowUpsRequest;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewAgentStepResponse;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewProgressResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.dto.InterviewReportResponse;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.interview.dto.ModelAnswerResponse;
import com.careertuner.interview.dto.RealtimeSessionResponse;
import com.careertuner.interview.dto.ScoreVoiceTranscriptRequest;
import com.careertuner.interview.dto.SessionPageResponse;
import com.careertuner.interview.dto.SessionReviewResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;
import com.careertuner.interview.realtime.InterviewRealtimeService;
import com.careertuner.interview.service.InterviewService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Validated
@RequiresConsent(ConsentType.AI_DATA)
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewRealtimeService realtimeService;
    // 사용자 모델 선택(요청 스코프) — 생성 엔드포인트에서만 set, 채점 경로엔 미적용(공정성). 게이트웨이가 읽는다.
    private final com.careertuner.interview.service.InterviewModelSelectionTrace interviewModelSelectionTrace;

    @GetMapping("/sessions")
    public ApiResponse<SessionPageResponse> listSessions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(interviewService.listSessions(authUser.id(), page, size));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@AuthenticationPrincipal AuthUser authUser,
                                           @PathVariable Long sessionId) {
        interviewService.deleteSession(authUser.id(), sessionId);
        return ApiResponse.ok();
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public ApiResponse<Void> markResumed(@AuthenticationPrincipal AuthUser authUser,
                                         @PathVariable Long sessionId) {
        interviewService.markResumed(authUser.id(), sessionId);
        return ApiResponse.ok();
    }

    @PostMapping("/sessions/{sessionId}/dispatch")
    public ApiResponse<Void> dispatchSession(@AuthenticationPrincipal AuthUser authUser,
                                             @PathVariable Long sessionId,
                                             @RequestBody(required = false) DispatchInterviewSessionRequest request) {
        DispatchInterviewSessionRequest body = request != null
                ? request
                : new DispatchInterviewSessionRequest(null);
        interviewService.dispatchSession(authUser.id(), sessionId, body.targetOrDefault());
        return ApiResponse.ok();
    }

    @PostMapping("/sessions")
    public ApiResponse<InterviewSessionResponse> createSession(@AuthenticationPrincipal AuthUser authUser,
                                                               @Valid @RequestBody CreateInterviewSessionRequest request) {
        return ApiResponse.ok(interviewService.createSession(authUser.id(), request));
    }

    @PostMapping("/sessions/{sessionId}/generate-questions")
    @RequiresAiCharge("INTERVIEW_QUESTION_GEN")
    public ApiResponse<List<InterviewQuestionResponse>> generateQuestions(@AuthenticationPrincipal AuthUser authUser,
                                                                          @PathVariable Long sessionId,
                                                                          @RequestHeader(AiChargeRequestSettlementService.ACKNOWLEDGEMENT_HEADER) String operationKey,
                                                                          @RequestBody(required = false) GenerateQuestionsRequest request,
                                                                          @RequestParam(required = false) String model) {
        GenerateQuestionsRequest body = request != null ? request : new GenerateQuestionsRequest(null, null);
        interviewModelSelectionTrace.set(com.careertuner.ai.common.model.RequestedAiModel.parse(model));
        try {
            return ApiResponse.ok(interviewService.generateQuestions(authUser.id(), sessionId, body, operationKey));
        } finally {
            interviewModelSelectionTrace.clear();
        }
    }

    @GetMapping("/sessions/{sessionId}/questions")
    public ApiResponse<List<InterviewQuestionResponse>> listQuestions(@AuthenticationPrincipal AuthUser authUser,
                                                                      @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.listQuestions(authUser.id(), sessionId));
    }

    @PostMapping("/questions/{questionId}/model-answer")
    @RequiresAiCharge("INTERVIEW_MODEL_ANSWER")
    public ApiResponse<ModelAnswerResponse> getModelAnswer(@AuthenticationPrincipal AuthUser authUser,
                                                           @PathVariable Long questionId,
                                                           @RequestParam(required = false) String model) {
        interviewModelSelectionTrace.set(com.careertuner.ai.common.model.RequestedAiModel.parse(model));
        try {
            return ApiResponse.ok(interviewService.getModelAnswer(authUser.id(), questionId));
        } finally {
            interviewModelSelectionTrace.clear();
        }
    }

    @PostMapping("/questions/{questionId}/answers")
    @RequiresAiCharge("INTERVIEW_ANSWER_EVAL")
    public ApiResponse<InterviewAnswerResponse> submitAnswer(@AuthenticationPrincipal AuthUser authUser,
                                                             @PathVariable Long questionId,
                                                             @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.ok(interviewService.submitAnswer(authUser.id(), questionId, request));
    }

    @DeleteMapping("/answers/{answerId}/media/{kind}")
    public ApiResponse<Void> deleteAnswerMedia(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long answerId,
                                               @PathVariable String kind) {
        interviewService.deleteAnswerMedia(authUser.id(), answerId, kind);
        return ApiResponse.ok();
    }

    @PostMapping("/questions/{questionId}/follow-ups")
    @RequiresAiCharge("INTERVIEW_FOLLOWUP_GEN")
    public ApiResponse<List<InterviewQuestionResponse>> generateFollowUps(@AuthenticationPrincipal AuthUser authUser,
                                                                          @PathVariable Long questionId,
                                                                          @RequestHeader(AiChargeRequestSettlementService.ACKNOWLEDGEMENT_HEADER) String operationKey,
                                                                          @RequestBody(required = false) GenerateFollowUpsRequest request,
                                                                          @RequestParam(required = false) String model) {
        interviewModelSelectionTrace.set(com.careertuner.ai.common.model.RequestedAiModel.parse(model));
        try {
            return ApiResponse.ok(interviewService.generateFollowUps(authUser.id(), questionId, request, operationKey));
        } finally {
            interviewModelSelectionTrace.clear();
        }
    }

    @GetMapping("/sessions/{sessionId}/progress")
    public ApiResponse<InterviewProgressResponse> getProgress(@AuthenticationPrincipal AuthUser authUser,
                                                              @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.getProgress(authUser.id(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/agent-steps")
    public ApiResponse<List<InterviewAgentStepResponse>> getAgentSteps(@AuthenticationPrincipal AuthUser authUser,
                                                                       @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.getAgentSteps(authUser.id(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/realtime")
    @RequiresAiCharge("INTERVIEW_VOICE_SESSION")
    public ApiResponse<RealtimeSessionResponse> createRealtimeSession(@AuthenticationPrincipal AuthUser authUser,
                                                                      @PathVariable Long sessionId,
                                                                      @RequestParam(required = false) @Min(1) @Max(6) Integer questionLimit) {
        return ApiResponse.ok(realtimeService.createSession(authUser.id(), sessionId, questionLimit));
    }

    @GetMapping("/sessions/{sessionId}/report")
    @RequiresAiCharge("INTERVIEW_REPORT")
    public ApiResponse<InterviewReportResponse> getReport(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long sessionId,
                                                          @RequestParam(required = false) String model) {
        interviewModelSelectionTrace.set(com.careertuner.ai.common.model.RequestedAiModel.parse(model));
        try {
            return ApiResponse.ok(interviewService.getReport(authUser.id(), sessionId));
        } finally {
            interviewModelSelectionTrace.clear();
        }
    }

    @GetMapping("/sessions/{sessionId}/review")
    public ApiResponse<SessionReviewResponse> getSessionReview(@AuthenticationPrincipal AuthUser authUser,
                                                               @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.getSessionReview(authUser.id(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/score-voice")
    @RequiresAiCharge("INTERVIEW_VOICE_SCORING")
    public ApiResponse<Integer> scoreVoiceTranscript(@AuthenticationPrincipal AuthUser authUser,
                                                     @PathVariable Long sessionId,
                                                     @Valid @RequestBody ScoreVoiceTranscriptRequest request) {
        return ApiResponse.ok(interviewService.scoreVoiceTranscript(authUser.id(), sessionId,
                request.transcript(), request.questionLimit()));
    }
}
