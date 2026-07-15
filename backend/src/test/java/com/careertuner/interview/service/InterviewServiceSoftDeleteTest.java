package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.InterviewMediaMapper;
import com.careertuner.file.service.FileService;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class InterviewServiceSoftDeleteTest {

    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final ApplicationCaseAccessService accessService = mock(ApplicationCaseAccessService.class);
    private final InterviewOpenAiClient aiClient = mock(InterviewOpenAiClient.class);
    private final InterviewAiUsageLogService aiUsageLogService = mock(InterviewAiUsageLogService.class);
    private final InterviewAgentOrchestrator orchestrator = mock(InterviewAgentOrchestrator.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final InterviewBackgroundExecutor backgroundExecutor = mock(InterviewBackgroundExecutor.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final FileService fileService = mock(FileService.class);

    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterviewServiceImpl(
                interviewMapper,
                accessService,
                aiClient,
                aiUsageLogService,
                orchestrator,
                objectMapper,
                backgroundExecutor,
                notificationService,
                fileService,
                mock(InterviewMediaMapper.class));
    }

    @Test
    void deletingSessionReclaimsOnlyOwnedLinkedOriginalMedia() {
        long userId = 1L;
        long sessionId = 10L;
        InterviewSession session = InterviewSession.builder()
                .id(sessionId).applicationCaseId(100L).mode("BASIC").build();
        InterviewAnswer answer = InterviewAnswer.builder().id(30L).questionId(20L).build();
        FileAsset ownedAudio = FileAsset.builder()
                .id(71L).ownerUserId(userId).kind("AUDIO")
                .refType("INTERVIEW_ANSWER").refId(answer.getId()).build();
        FileAsset foreignVideo = FileAsset.builder()
                .id(72L).ownerUserId(99L).kind("VIDEO")
                .refType("INTERVIEW_ANSWER").refId(answer.getId()).build();
        FileAsset unrelatedKind = FileAsset.builder()
                .id(73L).ownerUserId(userId).kind("ATTACHMENT")
                .refType("INTERVIEW_ANSWER").refId(answer.getId()).build();
        when(interviewMapper.findSessionByIdAndUserId(sessionId, userId)).thenReturn(session);
        when(interviewMapper.findAnswersBySessionId(sessionId)).thenReturn(List.of(answer));
        when(fileService.findLinkedFiles("INTERVIEW_ANSWER", answer.getId()))
                .thenReturn(List.of(ownedAudio, foreignVideo, unrelatedKind));
        when(interviewMapper.softDeleteSession(sessionId, userId)).thenReturn(1);

        service.deleteSession(userId, sessionId);

        verify(fileService).deleteOwnedLinked(
                userId, 71L, "AUDIO", "INTERVIEW_ANSWER", answer.getId());
        verify(fileService, never()).deleteOwnedLinked(
                userId, 72L, "VIDEO", "INTERVIEW_ANSWER", answer.getId());
        verify(fileService, never()).deleteOwnedLinked(
                userId, 73L, "ATTACHMENT", "INTERVIEW_ANSWER", answer.getId());
        verify(interviewMapper).softDeleteSession(sessionId, userId);
        verify(interviewMapper).softDeleteTrainingSamplesBySessionId(sessionId);
    }

    @Test
    void deletingUnknownOrForeignSessionCannotProbeOrDeleteMedia() {
        long userId = 1L;
        long sessionId = 10L;
        when(interviewMapper.findSessionByIdAndUserId(sessionId, userId)).thenReturn(null);

        BusinessException error = catchThrowableOfType(
                () -> service.deleteSession(userId, sessionId), BusinessException.class);

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        verify(interviewMapper, never()).findAnswersBySessionId(sessionId);
        verify(fileService, never()).findLinkedFiles(anyString(), any());
        verify(interviewMapper, never()).softDeleteSession(sessionId, userId);
        verify(interviewMapper, never()).softDeleteTrainingSamplesBySessionId(sessionId);
    }

    @Test
    void reportGenerationDoesNotNotifyWhenSessionWasDeletedConcurrently() {
        long userId = 1L;
        long sessionId = 10L;
        InterviewSession session = InterviewSession.builder()
                .id(sessionId)
                .applicationCaseId(100L)
                .mode("BASIC")
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        InterviewQuestion question = InterviewQuestion.builder()
                .id(20L)
                .interviewSessionId(sessionId)
                .question("자기소개를 해 주세요.")
                .questionType("EXPECTED")
                .sortOrder(0)
                .build();
        InterviewAnswer answer = InterviewAnswer.builder()
                .id(30L)
                .questionId(question.getId())
                .answerText("경험을 바탕으로 작성한 답변입니다.")
                .score(80)
                .feedback("구체적인 성과를 보완하세요.")
                .build();
        InterviewOpenAiClient.ReportPayload payload = new InterviewOpenAiClient.ReportPayload(
                80,
                List.of(new InterviewOpenAiClient.ReportCategory("직무 역량", 80)),
                List.of("핵심 경험을 더 구체화하세요."),
                new InterviewOpenAiClient.Usage("test-model", 1, 1, 2));

        when(interviewMapper.lockSessionByIdAndUserId(sessionId, userId)).thenReturn(session);
        when(interviewMapper.findQuestionsBySessionId(sessionId)).thenReturn(List.of(question));
        when(interviewMapper.findAnswersBySessionId(sessionId)).thenReturn(List.of(answer));
        when(interviewMapper.findLatestScoredSessionScore(session.getApplicationCaseId(), sessionId))
                .thenReturn(70);
        when(aiClient.generateReport(anyString())).thenReturn(payload);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(interviewMapper.updateSessionResult(
                eq(sessionId), eq(payload.totalScore()), anyString(), any(LocalDateTime.class)))
                .thenReturn(0);

        BusinessException error = catchThrowableOfType(
                () -> service.getReport(userId, sessionId), BusinessException.class);

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        verify(notificationService, never()).notify(any(Notification.class));
    }

    @Test
    void reportReadyNotificationOpensTheGeneratedReportTab() throws Exception {
        long userId = 1L;
        long sessionId = 10L;
        InterviewSession session = InterviewSession.builder()
                .id(sessionId)
                .applicationCaseId(100L)
                .mode("BASIC")
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        InterviewQuestion question = InterviewQuestion.builder()
                .id(20L)
                .interviewSessionId(sessionId)
                .question("자기소개를 해 주세요.")
                .questionType("EXPECTED")
                .sortOrder(0)
                .build();
        InterviewAnswer answer = InterviewAnswer.builder()
                .id(30L)
                .questionId(question.getId())
                .answerText("경험을 바탕으로 작성한 답변입니다.")
                .score(80)
                .feedback("구체적인 성과를 보완하세요.")
                .build();
        InterviewOpenAiClient.ReportPayload payload = new InterviewOpenAiClient.ReportPayload(
                80,
                List.of(new InterviewOpenAiClient.ReportCategory("직무 역량", 80)),
                List.of("핵심 경험을 더 구체화하세요."),
                new InterviewOpenAiClient.Usage("test-model", 1, 1, 2));

        when(interviewMapper.lockSessionByIdAndUserId(sessionId, userId)).thenReturn(session);
        when(interviewMapper.findQuestionsBySessionId(sessionId)).thenReturn(List.of(question));
        when(interviewMapper.findAnswersBySessionId(sessionId)).thenReturn(List.of(answer));
        when(interviewMapper.findLatestScoredSessionScore(session.getApplicationCaseId(), sessionId))
                .thenReturn(70);
        when(aiClient.generateReport(anyString())).thenReturn(payload);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(interviewMapper.updateSessionResult(
                eq(sessionId), eq(payload.totalScore()), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);

        service.getReport(userId, sessionId);

        verify(notificationService).notify(notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo("INTERVIEW_REPORT_READY");
        assertThat(notification.getValue().getLink())
                .isEqualTo("/interview/reports?session=10");
    }
}
