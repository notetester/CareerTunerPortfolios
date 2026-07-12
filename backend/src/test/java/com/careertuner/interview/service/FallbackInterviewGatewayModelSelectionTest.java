package com.careertuner.interview.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.interview.service.InterviewLlmGateway.Request;
import com.careertuner.interview.service.InterviewLlmGateway.Result;

/**
 * 면접 <b>생성</b> 게이트웨이 모델 선택 라우팅(요청 스코프 ThreadLocal). 채점(EVAL/Critic)은 별도 경로라 무관.
 * AUTO=현행(자체모델은 학습 task 화이트리스트만, 현재 빈 → Claude 우선), 명시 선택은 그 tier 부터 + 목업 안전망.
 */
class FallbackInterviewGatewayModelSelectionTest {

    private final OssLlmGateway oss = mock(OssLlmGateway.class);
    private final AnthropicLlmGateway anthropic = mock(AnthropicLlmGateway.class);
    private final OpenAiLlmGateway openAi = mock(OpenAiLlmGateway.class);
    private final MockInterviewLlmGateway mockGateway = mock(MockInterviewLlmGateway.class);
    private final InterviewEvalProperties evalProperties = mock(InterviewEvalProperties.class);
    private final InterviewModelSelectionTrace trace = new InterviewModelSelectionTrace();
    private final FallbackInterviewLlmGateway gateway =
            new FallbackInterviewLlmGateway(oss, anthropic, openAi, mockGateway, evalProperties, trace);

    private static final Request REQ = new Request("interview_questions", Map.of(), "sys", "user", "gpt-5");
    private final Result resp = new Result(null, null);

    @BeforeEach
    void setUp() {
        lenient().when(oss.complete(any())).thenReturn(resp);
        lenient().when(anthropic.complete(any())).thenReturn(resp);
        lenient().when(openAi.complete(any())).thenReturn(resp);
        lenient().when(mockGateway.complete(any())).thenReturn(resp);
    }

    @AfterEach
    void tearDown() {
        trace.clear();
    }

    @Test
    void autoUsesClaudeWhenAvailable() {
        // 자체모델 생성 화이트리스트가 비어 AUTO 는 Claude 우선(현행 동일).
        when(anthropic.available()).thenReturn(true);

        gateway.complete(REQ);

        verify(anthropic).complete(any());
        verify(oss, never()).complete(any());
        verify(openAi, never()).complete(any());
    }

    @Test
    void openAiChoiceIsolatesToOpenAi() {
        trace.set(RequestedAiModel.OPENAI);
        when(openAi.available()).thenReturn(true);

        gateway.complete(REQ);

        verify(openAi).complete(any());
        verify(oss, never()).complete(any());
        verify(anthropic, never()).complete(any());
    }

    @Test
    void careertunerChoiceTriesSelfBypassingGenerationWhitelist() {
        // 명시 CAREERTUNER 는 생성 화이트리스트(빈)를 우회해 서빙되면 자체모델을 시도한다.
        trace.set(RequestedAiModel.CAREERTUNER);
        when(oss.available()).thenReturn(true);

        gateway.complete(REQ);

        verify(oss).complete(any());
    }

    @Test
    void allProvidersUnavailableFallsToMockSafetyNet() {
        gateway.complete(REQ); // AUTO, 아무 provider 도 available 아님 → 목업.

        verify(mockGateway).complete(any());
    }
}
