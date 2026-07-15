package com.careertuner.ai.autoprep.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.autoprep.AutoPrepCancellation;
import com.careertuner.ai.autoprep.AutoPrepCancelledException;
import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepSlots;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.applicationcase.service.ApplicationCaseService;
import com.careertuner.community.service.CommunityPostService;
import com.careertuner.correction.service.CorrectionService;
import com.careertuner.fitanalysis.service.FitAnalysisService;
import com.careertuner.interview.service.InterviewService;
import com.careertuner.interview.dto.CreateInterviewSessionRequest;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.InterviewSessionResponse;
import com.careertuner.jobanalysis.service.JobAnalysisService;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.mapper.ProfileMapper;
import com.careertuner.profile.service.ProfilePortfolioService;

class AutoPrepHandlerCancellationTest {

    @Test
    void everyHandlerChecksCancellationBeforeDomainCall() {
        AutoPrepCancellation cancellation = new AutoPrepCancellation();
        cancellation.cancel();
        PrepStepContext context = new PrepStepContext(
                1L, 7L, new PrepSlots("카카오", "백엔드", "BASIC", 7L),
                "자소서", List.of(), Map.of(), cancellation);

        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileAiService profileAiService = mock(ProfileAiService.class);
        ProfilePortfolioService portfolioService = mock(ProfilePortfolioService.class);
        assertCancelled(() -> new ProfilePrepHandler(profileMapper, profileAiService, portfolioService)
                .handle(context, PrepProgress.NOOP));
        verifyNoInteractions(profileMapper, profileAiService, portfolioService);

        JobAnalysisService jobAnalysisService = mock(JobAnalysisService.class);
        ApplicationCaseService applicationCaseService = mock(ApplicationCaseService.class);
        assertCancelled(() -> new JobPrepHandler(jobAnalysisService, applicationCaseService)
                .handle(context, PrepProgress.NOOP));
        verifyNoInteractions(jobAnalysisService, applicationCaseService);

        FitAnalysisService fitAnalysisService = mock(FitAnalysisService.class);
        assertCancelled(() -> new FitPrepHandler(fitAnalysisService).handle(context, PrepProgress.NOOP));
        verifyNoInteractions(fitAnalysisService);

        CorrectionService correctionService = mock(CorrectionService.class);
        assertCancelled(() -> new WritePrepHandler(correctionService).handle(context, PrepProgress.NOOP));
        verifyNoInteractions(correctionService);

        InterviewService interviewService = mock(InterviewService.class);
        assertCancelled(() -> new InterviewPrepHandler(interviewService).handle(context, PrepProgress.NOOP));
        verifyNoInteractions(interviewService);

        CommunityPostService communityPostService = mock(CommunityPostService.class);
        assertCancelled(() -> new CommunityPrepHandler(communityPostService).handle(context, PrepProgress.NOOP));
        verifyNoInteractions(communityPostService);
    }

    @Test
    void interviewCancellationAfterSessionCreationSoftDeletesEmptySession() {
        AutoPrepCancellation cancellation = new AutoPrepCancellation();
        PrepStepContext context = context(cancellation);
        InterviewService interviewService = mock(InterviewService.class);
        when(interviewService.createSession(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(CreateInterviewSessionRequest.class)))
                .thenReturn(session(41L));
        PrepProgress cancelBeforeQuestions = (name, desc) -> {
            if ("질문 생성".equals(name)) cancellation.cancel();
        };

        assertCancelled(() -> new InterviewPrepHandler(interviewService)
                .handle(context, cancelBeforeQuestions));

        verify(interviewService).deleteSession(1L, 41L);
        verify(interviewService, never()).generateQuestions(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(GenerateQuestionsRequest.class));
    }

    @Test
    void interviewQuestionFailureSoftDeletesCreatedSession() {
        AutoPrepCancellation cancellation = new AutoPrepCancellation();
        PrepStepContext context = context(cancellation);
        InterviewService interviewService = mock(InterviewService.class);
        when(interviewService.createSession(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(CreateInterviewSessionRequest.class)))
                .thenReturn(session(42L));
        when(interviewService.generateQuestions(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.any(GenerateQuestionsRequest.class)))
                .thenThrow(new IllegalStateException("provider failure"));

        assertThatThrownBy(() -> new InterviewPrepHandler(interviewService)
                .handle(context, PrepProgress.NOOP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider failure");

        verify(interviewService).deleteSession(1L, 42L);
    }

    @Test
    void interviewSuccessKeepsCreatedSession() {
        AutoPrepCancellation cancellation = new AutoPrepCancellation();
        PrepStepContext context = context(cancellation);
        InterviewService interviewService = mock(InterviewService.class);
        when(interviewService.createSession(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(CreateInterviewSessionRequest.class)))
                .thenReturn(session(43L));
        when(interviewService.generateQuestions(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(43L),
                org.mockito.ArgumentMatchers.any(GenerateQuestionsRequest.class)))
                .thenReturn(List.of());

        new InterviewPrepHandler(interviewService).handle(context, PrepProgress.NOOP);

        verify(interviewService, never()).deleteSession(1L, 43L);
    }

    @Test
    void interviewCancellationDuringCompletedQuestionGenerationKeepsConsistentSession() {
        AutoPrepCancellation cancellation = new AutoPrepCancellation();
        PrepStepContext context = context(cancellation);
        InterviewService interviewService = mock(InterviewService.class);
        when(interviewService.createSession(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(CreateInterviewSessionRequest.class)))
                .thenReturn(session(44L));
        when(interviewService.generateQuestions(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(44L),
                org.mockito.ArgumentMatchers.any(GenerateQuestionsRequest.class)))
                .thenAnswer(invocation -> {
                    cancellation.cancel();
                    return List.of();
                });

        new InterviewPrepHandler(interviewService).handle(context, PrepProgress.NOOP);

        // 질문 생성 서비스가 정상 반환하면 질문·알림·afterCommit 작업이 이미 한 단위로 확정됐다.
        // 바깥 runPart가 취소 결과의 누적만 막고, 일관된 세션 자체는 되돌리지 않는다.
        verify(interviewService, never()).deleteSession(1L, 44L);
    }

    private static PrepStepContext context(AutoPrepCancellation cancellation) {
        return new PrepStepContext(
                1L, 7L, new PrepSlots("카카오", "백엔드", "BASIC", 7L),
                "자소서", List.of(), Map.of(), cancellation);
    }

    private static InterviewSessionResponse session(Long id) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 14, 12, 0);
        return new InterviewSessionResponse(
                id, 7L, "BASIC", now, null, 0, 0, false,
                null, null, null, null, null, now);
    }

    private static void assertCancelled(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AutoPrepCancelledException.class);
    }
}
