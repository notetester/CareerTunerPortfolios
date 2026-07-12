package com.careertuner.interview.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.interview.domain.InterviewAnswer;
import com.careertuner.interview.domain.InterviewMediaAnalysis;
import com.careertuner.interview.domain.InterviewQuestion;
import com.careertuner.interview.domain.InterviewSession;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.dto.SaveMediaAnalysisRequest;
import com.careertuner.interview.media.dto.VoiceScoreRequest;
import com.careertuner.interview.media.dto.VoiceScoreResponse;
import com.careertuner.interview.service.InterviewAiUsageLogService;
import com.careertuner.interview.service.InterviewOpenAiClient;

import tools.jackson.databind.ObjectMapper;

class InterviewMediaServiceLifecycleTest {

    private final InterviewMediaMapper mediaMapper = mock(InterviewMediaMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final InterviewNonverbalClient nonverbalClient = mock(InterviewNonverbalClient.class);
    private final InterviewAiUsageLogService usageLogService = mock(InterviewAiUsageLogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InterviewMediaService service = new InterviewMediaService(
            mediaMapper, interviewMapper, nonverbalClient, objectMapper, usageLogService);

    @Test
    void answerScopedAnalysisRequiresMatchingOwnedSessionQuestionAndAnswer() {
        stubOwnedSession();
        when(interviewMapper.findQuestionByIdAndUserId(31L, 7L)).thenReturn(
                InterviewQuestion.builder().id(31L).interviewSessionId(11L).build());
        when(interviewMapper.findAnswerByIdAndUserId(41L, 7L)).thenReturn(
                InterviewAnswer.builder().id(41L).questionId(31L).build());
        var detail = objectMapper.createObjectNode()
                .put("voiceScore", 81)
                .put("visualScore", 86);

        service.save(7L, 11L, new SaveMediaAnalysisRequest(
                "AVATAR", 31L, 41L, null, null, 81, detail));

        ArgumentCaptor<InterviewMediaAnalysis> saved = ArgumentCaptor.forClass(InterviewMediaAnalysis.class);
        verify(mediaMapper).insertMediaAnalysis(saved.capture());
        assertThat(saved.getValue().getQuestionId()).isEqualTo(31L);
        assertThat(saved.getValue().getAnswerId()).isEqualTo(41L);
        assertThat(saved.getValue().getScoreDetail()).contains("voiceScore").contains("visualScore");
    }

    @Test
    void mismatchedAnswerLinkIsRejectedBeforeInsert() {
        stubOwnedSession();
        when(interviewMapper.findQuestionByIdAndUserId(31L, 7L)).thenReturn(
                InterviewQuestion.builder().id(31L).interviewSessionId(11L).build());
        when(interviewMapper.findAnswerByIdAndUserId(41L, 7L)).thenReturn(
                InterviewAnswer.builder().id(41L).questionId(99L).build());

        BusinessException error = catchThrowableOfType(
                () -> service.save(7L, 11L, new SaveMediaAnalysisRequest(
                        "VOICE", 31L, 41L, null, null, 80, null)),
                BusinessException.class);

        assertThat(error.getMessage()).contains("현재 세션");
        verify(mediaMapper, never()).insertMediaAnalysis(any());
    }

    @Test
    void successfulVoiceScoringRecordsUsageForChargeSettlement() {
        stubOwnedSession();
        VoiceScoreRequest request = new VoiceScoreRequest("ZmFrZQ==", "webm", 10, 0, 1.0);
        VoiceScoreResponse response = new VoiceScoreResponse(80, null, null, "rule");
        when(nonverbalClient.scoreVoice("ZmFrZQ==", "webm", 10, 0, 1.0)).thenReturn(response);

        assertThat(service.scoreVoice(7L, 11L, request)).isSameAs(response);

        ArgumentCaptor<InterviewOpenAiClient.Usage> usage =
                ArgumentCaptor.forClass(InterviewOpenAiClient.Usage.class);
        verify(usageLogService).recordSuccess(eq(7L), eq(21L), eq("INTERVIEW_VOICE_SCORING"), usage.capture());
        assertThat(usage.getValue().model()).isEqualTo("careertuner-nonverbal");
        assertThat(usage.getValue().totalTokens()).isZero();
    }

    private void stubOwnedSession() {
        InterviewSession session = InterviewSession.builder().id(11L).applicationCaseId(21L).build();
        when(interviewMapper.findSessionByIdAndUserId(11L, 7L)).thenReturn(session);
        when(interviewMapper.lockSessionByIdAndUserId(11L, 7L)).thenReturn(session);
    }
}
