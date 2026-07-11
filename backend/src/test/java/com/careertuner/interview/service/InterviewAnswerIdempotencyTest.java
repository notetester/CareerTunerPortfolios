package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.QueryTimeoutException;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.InterviewMediaMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class InterviewAnswerIdempotencyTest {

    private static final long USER_ID = 7L;
    private static final long QUESTION_ID = 21L;
    private static final String SUBMISSION_ID = "8e5bea9a-cc6b-4b6a-bdd7-8f3b8f3dd8ce";

    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
    private final InterviewAgentOrchestrator orchestrator = mock(InterviewAgentOrchestrator.class);
    private final FileService fileService = mock(FileService.class);
    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewServiceImpl(
                interviewMapper,
                accessService,
                mock(InterviewOpenAiClient.class),
                mock(InterviewAiUsageLogService.class),
                orchestrator,
                mock(ObjectMapper.class),
                mock(InterviewBackgroundExecutor.class),
                mock(NotificationService.class),
                fileService,
                mock(InterviewMediaMapper.class));
        stubOwnedQuestion();
    }

    @Test
    void reservationIsAcquiredBeforeAiAndCompletedAfterOriginalMediaClaim() {
        when(interviewMapper.insertAnswerReservation(any())).thenAnswer(invocation -> {
            InterviewAnswer pending = invocation.getArgument(0, InterviewAnswer.class);
            assertThat(pending.getSubmissionStatus()).isEqualTo("PENDING");
            assertThat(pending.getAnswerText()).isNull();
            pending.setId(501L);
            return 1;
        });
        when(orchestrator.evaluateAnswer(
                eq(USER_ID), any(), any(), any(), anyString(), eq("모범답안")))
                .thenReturn(new InterviewAgentOrchestrator.OrchestratedEvaluation(
                        88, "구체적입니다.", "개선 답변", "PASS", "ok"));
        when(fileService.claimOwnedPendingFile(
                USER_ID, 91L, "AUDIO", "INTERVIEW_ANSWER", 501L))
                .thenReturn(FileAsset.builder()
                        .id(91L).ownerUserId(USER_ID).kind("AUDIO")
                        .refType("INTERVIEW_ANSWER").refId(501L).build());
        when(interviewMapper.completeAnswerReservation(any())).thenReturn(1);

        InterviewAnswerResponse result = service.submitAnswer(
                USER_ID,
                QUESTION_ID,
                request(SUBMISSION_ID, 91L));

        InOrder order = inOrder(interviewMapper, orchestrator, fileService);
        order.verify(interviewMapper).insertAnswerReservation(any());
        order.verify(orchestrator).evaluateAnswer(
                eq(USER_ID), any(), any(), any(), anyString(), eq("모범답안"));
        order.verify(fileService).claimOwnedPendingFile(
                USER_ID, 91L, "AUDIO", "INTERVIEW_ANSWER", 501L);
        order.verify(interviewMapper).completeAnswerReservation(any());
        assertThat(result.id()).isEqualTo(501L);
        assertThat(result.clientSubmissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(result.submissionStatus()).isEqualTo("COMPLETED");
        assertThat(result.audioUrl()).isEqualTo("/api/file/91/content");
        verify(interviewMapper).invalidateSessionResult(31L);
        verify(interviewMapper, never()).insertAnswer(any());
    }

    @Test
    void completedRetryReturnsStoredResultWithoutAiOrSecondMediaClaim() {
        InterviewAnswer completed = InterviewAnswer.builder()
                .id(501L)
                .questionId(QUESTION_ID)
                .clientSubmissionId(SUBMISSION_ID)
                .submissionStatus("COMPLETED")
                .answerText("이미 저장된 답변")
                .audioUrl("/api/file/91/content")
                .score(88)
                .build();
        when(interviewMapper.insertAnswerReservation(any())).thenReturn(0);
        when(interviewMapper.findAnswerByQuestionIdAndClientSubmissionId(QUESTION_ID, SUBMISSION_ID))
                .thenReturn(completed);

        InterviewAnswerResponse result = service.submitAnswer(
                USER_ID,
                QUESTION_ID,
                request(SUBMISSION_ID, 91L));

        assertThat(result.id()).isEqualTo(501L);
        assertThat(result.answerText()).isEqualTo("이미 저장된 답변");
        verify(orchestrator, never()).evaluateAnswer(any(), any(), any(), any(), anyString(), any());
        verify(fileService, never()).claimOwnedPendingFile(any(), any(), anyString(), anyString(), any());
        verify(interviewMapper, never()).completeAnswerReservation(any());
        verify(interviewMapper, never()).invalidateSessionResult(any());
    }

    @Test
    void pendingDuplicateReturnsConflictWithoutStartingAi() {
        when(interviewMapper.insertAnswerReservation(any())).thenReturn(0);
        when(interviewMapper.findAnswerByQuestionIdAndClientSubmissionId(QUESTION_ID, SUBMISSION_ID))
                .thenReturn(InterviewAnswer.builder()
                        .id(501L)
                        .questionId(QUESTION_ID)
                        .clientSubmissionId(SUBMISSION_ID)
                        .submissionStatus("PENDING")
                        .build());

        BusinessException error = catchThrowableOfType(
                () -> service.submitAnswer(USER_ID, QUESTION_ID, request(SUBMISSION_ID, null)),
                BusinessException.class);

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        verify(orchestrator, never()).evaluateAnswer(any(), any(), any(), any(), anyString(), any());
        verify(fileService, never()).claimOwnedPendingFile(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void concurrentReservationWaitIsBoundedAndReportedAsInProgress() {
        when(interviewMapper.insertAnswerReservation(any()))
                .thenThrow(new QueryTimeoutException("reservation lock wait"));

        BusinessException error = catchThrowableOfType(
                () -> service.submitAnswer(USER_ID, QUESTION_ID, request(SUBMISSION_ID, null)),
                BusinessException.class);

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(error.getMessage()).contains("처리 중");
        verify(orchestrator, never()).evaluateAnswer(any(), any(), any(), any(), anyString(), any());
        verify(fileService, never()).claimOwnedPendingFile(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void invalidSubmissionIdIsRejectedBeforeReservationOrAi() {
        BusinessException error = catchThrowableOfType(
                () -> service.submitAnswer(USER_ID, QUESTION_ID, request("not-a-uuid", null)),
                BusinessException.class);

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        verify(interviewMapper, never()).insertAnswerReservation(any());
        verify(orchestrator, never()).evaluateAnswer(any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void evaluationFailureDoesNotCompleteReservationSoTransactionCanRollBackForRetry() {
        when(interviewMapper.insertAnswerReservation(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, InterviewAnswer.class).setId(501L);
            return 1;
        });
        when(orchestrator.evaluateAnswer(
                eq(USER_ID), any(), any(), any(), anyString(), eq("모범답안")))
                .thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));

        BusinessException error = catchThrowableOfType(
                () -> service.submitAnswer(USER_ID, QUESTION_ID, request(SUBMISSION_ID, null)),
                BusinessException.class);

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
        verify(interviewMapper).insertAnswerReservation(any());
        verify(interviewMapper, never()).completeAnswerReservation(any());
        verify(fileService, never()).claimOwnedPendingFile(any(), any(), anyString(), anyString(), any());
    }

    private SubmitAnswerRequest request(String submissionId, Long audioFileId) {
        return new SubmitAnswerRequest(
                "성과를 수치로 설명한 답변입니다.",
                "https://client.invalid/audio",
                null,
                audioFileId,
                null,
                "모범답안",
                submissionId,
                null,
                null);
    }

    private void stubOwnedQuestion() {
        InterviewQuestion question = InterviewQuestion.builder()
                .id(QUESTION_ID)
                .interviewSessionId(31L)
                .question("경험을 설명해 주세요.")
                .modelAnswer("모범답안")
                .build();
        InterviewSession session = InterviewSession.builder()
                .id(31L)
                .applicationCaseId(41L)
                .mode("BASIC")
                .build();
        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(41L)
                .userId(USER_ID)
                .companyName("커리어튜너")
                .build();
        when(interviewMapper.findQuestionByIdAndUserId(QUESTION_ID, USER_ID)).thenReturn(question);
        when(interviewMapper.lockQuestionByIdAndUserId(QUESTION_ID, USER_ID)).thenReturn(question);
        when(interviewMapper.findSessionByIdAndUserId(31L, USER_ID)).thenReturn(session);
        when(interviewMapper.lockSessionByIdAndUserId(31L, USER_ID)).thenReturn(session);
        when(accessService.requireOwned(USER_ID, 41L)).thenReturn(applicationCase);
    }
}
