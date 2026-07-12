package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.service.FileService;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewMediaAnalysis;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.dto.GenerateQuestionsRequest;
import com.careertuner.interview.dto.SessionReviewResponse;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.InterviewMediaMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class InterviewDerivedLifecycleTest {

    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final InterviewMediaMapper mediaMapper = mock(InterviewMediaMapper.class);
    private final InterviewOpenAiClient aiClient = mock(InterviewOpenAiClient.class);
    private final InterviewServiceImpl service = new InterviewServiceImpl(
            interviewMapper,
            mock(ApplicationCaseAccessService.class),
            aiClient,
            mock(InterviewAiUsageLogService.class),
            mock(InterviewAgentOrchestrator.class),
            new ObjectMapper(),
            mock(InterviewBackgroundExecutor.class),
            mock(NotificationService.class),
            mock(FileService.class),
            mediaMapper);

    @Test
    void modelAnswerGenerationRunsInsideQuestionLockTransaction() throws Exception {
        assertThat(InterviewServiceImpl.class
                .getMethod("getModelAnswer", Long.class, Long.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void questionRegenerationStopsBeforeAiWhenDerivedDataExists() {
        InterviewSession session = InterviewSession.builder()
                .id(11L).applicationCaseId(21L).mode("BASIC").build();
        when(interviewMapper.lockSessionByIdAndUserId(11L, 7L)).thenReturn(session);
        when(interviewMapper.insertAiOperationReservation(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("INTERVIEW_QUESTION_GEN"),
                org.mockito.ArgumentMatchers.eq(11L),
                anyString())).thenReturn(1);
        when(interviewMapper.hasQuestionRegenerationBlockers(11L)).thenReturn(true);

        BusinessException error = catchThrowableOfType(
                () -> service.generateQuestions(7L, 11L, new GenerateQuestionsRequest("BASIC", 6)),
                BusinessException.class);

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(error.getMessage()).contains("새 면접 세션");
        verify(aiClient, never()).generateQuestions(any(), anyString(), anyString(), any(Integer.class));
        verify(interviewMapper, never()).softDeleteQuestionsBySessionId(11L);
    }

    @Test
    void reviewRestoresAnswerScopedVoiceAndVisualScores() {
        InterviewSession session = InterviewSession.builder()
                .id(11L).applicationCaseId(21L).mode("BASIC").build();
        InterviewQuestion question = InterviewQuestion.builder()
                .id(31L).interviewSessionId(11L).question("질문").build();
        InterviewAnswer answer = InterviewAnswer.builder()
                .id(41L).questionId(31L).answerText("답변").score(80).build();
        InterviewMediaAnalysis analysis = InterviewMediaAnalysis.builder()
                .id(51L).interviewSessionId(11L).questionId(31L).answerId(41L)
                .kind("AVATAR").score(83)
                .scoreDetail("{\"voiceScore\":81,\"visualScore\":86}")
                .build();
        when(interviewMapper.findSessionByIdAndUserId(11L, 7L)).thenReturn(session);
        when(interviewMapper.findQuestionsBySessionId(11L)).thenReturn(List.of(question));
        when(interviewMapper.findAnswersBySessionId(11L)).thenReturn(List.of(answer));
        when(mediaMapper.findBySessionId(11L)).thenReturn(List.of(analysis));

        SessionReviewResponse response = service.getSessionReview(7L, 11L);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.voiceScore()).isEqualTo(81);
            assertThat(item.visualScore()).isEqualTo(86);
        });
    }
}
