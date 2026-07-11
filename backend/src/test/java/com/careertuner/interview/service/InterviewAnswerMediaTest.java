package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.dto.InterviewAnswerResponse;
import com.careertuner.interview.dto.SubmitAnswerRequest;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

class InterviewAnswerMediaTest {

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
                fileService);
    }

    @Test
    void submittedAudioIsClaimedByAnswerAndClientUrlIsReplaced() {
        long userId = 7L;
        long questionId = 21L;
        stubOwnedQuestion(userId, questionId);
        when(orchestrator.evaluateAnswer(
                eq(userId), any(), any(), any(), anyString(), eq("모범답안")))
                .thenReturn(new InterviewAgentOrchestrator.OrchestratedEvaluation(
                        88, "구체적입니다.", "개선 답변", "PASS", "ok"));
        doAnswer(invocation -> {
            invocation.getArgument(0, InterviewAnswer.class).setId(501L);
            return null;
        }).when(interviewMapper).insertAnswer(any(InterviewAnswer.class));
        when(fileService.claimOwnedPendingFile(
                userId, 91L, "AUDIO", "INTERVIEW_ANSWER", 501L))
                .thenReturn(FileAsset.builder()
                        .id(91L).ownerUserId(userId).kind("AUDIO")
                        .refType("INTERVIEW_ANSWER").refId(501L).build());

        InterviewAnswerResponse result = service.submitAnswer(userId, questionId,
                new SubmitAnswerRequest(
                        "성과를 수치로 설명한 답변입니다.",
                        "https://attacker.invalid/audio", null, 91L, null, "모범답안"));

        assertThat(result.audioUrl()).isEqualTo("/api/file/91/content");
        verify(fileService).claimOwnedPendingFile(
                userId, 91L, "AUDIO", "INTERVIEW_ANSWER", 501L);
        verify(interviewMapper).updateAnswerMediaUrls(501L, "/api/file/91/content", null);
    }

    @Test
    void ownerCanDeleteLinkedVideoAndAnswerUrlTogether() {
        long userId = 8L;
        long answerId = 601L;
        when(interviewMapper.findAnswerByIdAndUserId(answerId, userId)).thenReturn(
                InterviewAnswer.builder().id(answerId).questionId(22L)
                        .videoUrl("/api/file/92/content").build());
        FileAsset linked = FileAsset.builder()
                .id(92L).ownerUserId(userId).kind("VIDEO")
                .refType("INTERVIEW_ANSWER").refId(answerId).build();
        when(fileService.findLinkedFiles("INTERVIEW_ANSWER", answerId))
                .thenReturn(List.of(linked));

        service.deleteAnswerMedia(userId, answerId, "video");

        verify(fileService).deleteOwnedLinked(
                userId, 92L, "VIDEO", "INTERVIEW_ANSWER", answerId);
        verify(interviewMapper).clearAnswerVideoUrl(answerId);
    }

    private void stubOwnedQuestion(long userId, long questionId) {
        InterviewQuestion question = InterviewQuestion.builder()
                .id(questionId).interviewSessionId(31L)
                .question("경험을 설명해 주세요.")
                .modelAnswer("모범답안").build();
        InterviewSession session = InterviewSession.builder()
                .id(31L).applicationCaseId(41L).mode("BASIC").build();
        ApplicationCase applicationCase = ApplicationCase.builder()
                .id(41L).userId(userId).companyName("커리어튜너").build();
        when(interviewMapper.findQuestionByIdAndUserId(questionId, userId)).thenReturn(question);
        when(interviewMapper.findSessionByIdAndUserId(31L, userId)).thenReturn(session);
        when(accessService.requireOwned(userId, 41L)).thenReturn(applicationCase);
    }
}
