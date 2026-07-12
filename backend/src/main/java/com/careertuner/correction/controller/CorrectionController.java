package com.careertuner.correction.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.policy.RequiresConsent;
import com.careertuner.correction.dto.CorrectionCreateRequest;
import com.careertuner.correction.dto.CorrectionInterviewSourceResponse;
import com.careertuner.correction.dto.CorrectionResponse;
import com.careertuner.correction.dto.CorrectionWarmupResponse;
import com.careertuner.correction.ai.CorrectionModelWarmupService;
import com.careertuner.correction.service.CorrectionService;
import com.careertuner.correction.service.CorrectionSourceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/corrections")
@RequiredArgsConstructor
@RequiresConsent(ConsentType.AI_DATA)
public class CorrectionController {

    private final CorrectionService correctionService;
    private final CorrectionSourceService sourceService;
    private final CorrectionModelWarmupService warmupService;

    @PostMapping("/warmup")
    public ApiResponse<CorrectionWarmupResponse> warmup(@AuthenticationPrincipal AuthUser authUser) {
        authUser.id();
        return ApiResponse.ok(warmupService.warmAsync("CORRECTION_PAGE"));
    }

    @PostMapping
    public ApiResponse<CorrectionResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                  @Valid @RequestBody CorrectionCreateRequest request,
                                                  @RequestParam(required = false) String model) {
        // model 로 첨삭 모델을 명시 선택(AUTO/CAREERTUNER/CLAUDE/OPENAI, 기본 AUTO=현행 폴백).
        return ApiResponse.ok(correctionService.create(authUser.id(), request, RequestedAiModel.parse(model)));
    }

    @GetMapping
    public ApiResponse<List<CorrectionResponse>> list(@AuthenticationPrincipal AuthUser authUser,
                                                      @RequestParam(required = false) Long applicationCaseId,
                                                      @RequestParam(required = false) String correctionType,
                                                      @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(correctionService.list(authUser.id(), applicationCaseId, correctionType, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<CorrectionResponse> get(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long id) {
        return ApiResponse.ok(correctionService.get(authUser.id(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id) {
        correctionService.delete(authUser.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/sources/interview-answers/{answerId}")
    public ApiResponse<CorrectionInterviewSourceResponse> interviewAnswerSource(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long answerId) {
        return ApiResponse.ok(sourceService.interviewAnswer(authUser.id(), answerId));
    }
}
