package com.careertuner.interview.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateFollowUpsRequest;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewAgentStepResponse;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.InterviewProgressResponse;
import com.careertuner.interview.dto.InterviewQuestionResponse;
import com.careertuner.interview.dto.InterviewReportResponse;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.interview.dto.RealtimeSessionResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;
import com.careertuner.interview.realtime.InterviewRealtimeService;
import com.careertuner.interview.service.InterviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewRealtimeService realtimeService;

    @GetMapping("/sessions")
    public ApiResponse<List<InterviewSessionResponse>> listSessions(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(interviewService.listSessions(authUser.id()));
    }

    @PostMapping("/sessions")
    public ApiResponse<InterviewSessionResponse> createSession(@AuthenticationPrincipal AuthUser authUser,
                                                               @Valid @RequestBody CreateInterviewSessionRequest request) {
        return ApiResponse.ok(interviewService.createSession(authUser.id(), request));
    }

    @PostMapping("/sessions/{sessionId}/generate-questions")
    public ApiResponse<List<InterviewQuestionResponse>> generateQuestions(@AuthenticationPrincipal AuthUser authUser,
                                                                          @PathVariable Long sessionId,
                                                                          @RequestBody(required = false) GenerateQuestionsRequest request) {
        GenerateQuestionsRequest body = request != null ? request : new GenerateQuestionsRequest(null, null);
        return ApiResponse.ok(interviewService.generateQuestions(authUser.id(), sessionId, body));
    }

    @GetMapping("/sessions/{sessionId}/questions")
    public ApiResponse<List<InterviewQuestionResponse>> listQuestions(@AuthenticationPrincipal AuthUser authUser,
                                                                      @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.listQuestions(authUser.id(), sessionId));
    }

    @PostMapping("/questions/{questionId}/answers")
    public ApiResponse<InterviewAnswerResponse> submitAnswer(@AuthenticationPrincipal AuthUser authUser,
                                                             @PathVariable Long questionId,
                                                             @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.ok(interviewService.submitAnswer(authUser.id(), questionId, request));
    }

    @PostMapping("/questions/{questionId}/follow-ups")
    public ApiResponse<List<InterviewQuestionResponse>> generateFollowUps(@AuthenticationPrincipal AuthUser authUser,
                                                                          @PathVariable Long questionId,
                                                                          @RequestBody(required = false) GenerateFollowUpsRequest request) {
        return ApiResponse.ok(interviewService.generateFollowUps(authUser.id(), questionId, request));
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
    public ApiResponse<RealtimeSessionResponse> createRealtimeSession(@AuthenticationPrincipal AuthUser authUser,
                                                                      @PathVariable Long sessionId) {
        return ApiResponse.ok(realtimeService.createSession(authUser.id(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/report")
    public ApiResponse<InterviewReportResponse> getReport(@AuthenticationPrincipal AuthUser authUser,
                                                          @PathVariable Long sessionId) {
        return ApiResponse.ok(interviewService.getReport(authUser.id(), sessionId));
    }
}
