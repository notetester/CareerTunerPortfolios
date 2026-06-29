package com.careertuner.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

/**
 * 면접 채점이 자체모델(OSS) 실패/미서빙 시에도 Claude/OpenAI 로 폴백해 끊기지 않는지 검증.
 */
class FallbackInterviewAnswerEvaluatorTest {

    private final OssAnswerEvaluator oss = mock(OssAnswerEvaluator.class);
    private final InterviewOpenAiClient openAi = mock(InterviewOpenAiClient.class);
    private final InterviewEvalProperties props = mock(InterviewEvalProperties.class);

    private final ApplicationCase appCase = ApplicationCase.builder()
            .companyName("테스트회사").jobTitle("백엔드 개발자").build();

    private final InterviewOpenAiClient.Usage usage = new InterviewOpenAiClient.Usage("m", 0, 0, 0);

    @Test
    void 자체모델_채점실패시_OpenAi클라이언트로_폴백() {
        when(props.configured()).thenReturn(true);
        when(oss.evaluateAnswer(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "자체모델 다운"));
        InterviewOpenAiClient.AnswerEvaluation expected =
                new InterviewOpenAiClient.AnswerEvaluation(80, "피드백", "개선답변", usage);
        when(openAi.evaluateAnswer(anyString(), anyString(), any(), any(), any())).thenReturn(expected);

        FallbackInterviewAnswerEvaluator evaluator = new FallbackInterviewAnswerEvaluator(oss, openAi, props);
        InterviewOpenAiClient.AnswerEvaluation result =
                evaluator.evaluateAnswer("질문", "답변", appCase, null, null);

        assertThat(result).isEqualTo(expected);
        verify(openAi).evaluateAnswer(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void 자체모델_미설정시_OpenAi클라이언트_직행() {
        when(props.configured()).thenReturn(false);
        InterviewOpenAiClient.AnswerEvaluation expected =
                new InterviewOpenAiClient.AnswerEvaluation(70, "fb", "imp", usage);
        when(openAi.evaluateAnswer(anyString(), anyString(), any(), any(), any())).thenReturn(expected);

        FallbackInterviewAnswerEvaluator evaluator = new FallbackInterviewAnswerEvaluator(oss, openAi, props);
        InterviewOpenAiClient.AnswerEvaluation result =
                evaluator.evaluateAnswer("질문", "답변", appCase, null, null);

        assertThat(result).isEqualTo(expected);
        verify(oss, never()).evaluateAnswer(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void 자체모델_정상시_OSS결과_사용() {
        when(props.configured()).thenReturn(true);
        InterviewOpenAiClient.AnswerEvaluation ossResult =
                new InterviewOpenAiClient.AnswerEvaluation(90, "oss-fb", "oss-imp", usage);
        when(oss.evaluateAnswer(anyString(), anyString(), any(), any(), any())).thenReturn(ossResult);

        FallbackInterviewAnswerEvaluator evaluator = new FallbackInterviewAnswerEvaluator(oss, openAi, props);
        InterviewOpenAiClient.AnswerEvaluation result =
                evaluator.evaluateAnswer("질문", "답변", appCase, null, null);

        assertThat(result).isEqualTo(ossResult);
        verify(openAi, never()).evaluateAnswer(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void Critic_자체모델실패시_OpenAi클라이언트로_폴백() {
        when(props.configured()).thenReturn(true);
        when(oss.critiqueEvaluation(anyString(), anyString(), anyInt(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "자체모델 다운"));
        InterviewOpenAiClient.CritiqueResult expected =
                new InterviewOpenAiClient.CritiqueResult(75, "유지", "사유", usage);
        when(openAi.critiqueEvaluation(anyString(), anyString(), eq(75), any(), any())).thenReturn(expected);

        FallbackInterviewAnswerEvaluator evaluator = new FallbackInterviewAnswerEvaluator(oss, openAi, props);
        InterviewOpenAiClient.CritiqueResult result =
                evaluator.critiqueEvaluation("질문", "답변", 75, "원피드백", null);

        assertThat(result).isEqualTo(expected);
        verify(openAi).critiqueEvaluation(anyString(), anyString(), eq(75), any(), any());
    }
}
