package com.careertuner.jobanalysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.JobAnalysisPayload;
import com.careertuner.applicationcase.service.OpenAiResponsesClient.Usage;

/**
 * 공고 분석 폴백 디스패처(OSS→Claude→OpenAI→Mock) 검증.
 * provider 기본값(openai)이라 OSS 는 비활성 상태에서 Claude→OpenAI→Mock 분기를 확인한다.
 */
class JobAnalysisAiProviderTest {

    private final ApplicationCase command = ApplicationCase.builder()
            .id(1L).companyName("테스트기업").jobTitle("백엔드 개발자").build();

    @SuppressWarnings("unchecked")
    private JobAnalysisAiProvider provider(AnthropicJobAnalysisService anthropic,
                                           OpenAiJobAnalysisService openAi,
                                           MockJobAnalysisService mockService) {
        ObjectProvider<OssJobAnalysisClient> ossProvider = mock(ObjectProvider.class);
        when(ossProvider.getIfAvailable()).thenReturn(null); // OSS 비활성
        return new JobAnalysisAiProvider(
                new JobAnalysisAiProperties(), // 기본 provider=openai → ossAvailable=false
                anthropic, openAi, mockService, ossProvider, new JobAnalysisQualityGate(null));
    }

    private JobAnalysisPayload tagged(String model) {
        return new JobAnalysisPayload(null, null, "[]", "[]", null, null, null,
                "요약", "[]", "[]", new Usage(model, 0, 0, 0));
    }

    @Test
    void usesClaudeWhenConfigured() {
        AnthropicJobAnalysisService anthropic = mock(AnthropicJobAnalysisService.class);
        OpenAiJobAnalysisService openAi = mock(OpenAiJobAnalysisService.class);
        MockJobAnalysisService mockService = mock(MockJobAnalysisService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.analyze(command, "src")).thenReturn(tagged("claude-haiku"));

        JobAnalysisPayload result = provider(anthropic, openAi, mockService).analyze(command, "src");

        assertThat(result.usage().model()).isEqualTo("claude-haiku");
        verify(openAi, never()).analyze(command, "src");
        verify(mockService, never()).analyze(command, "src");
    }

    @Test
    void fallsBackToOpenAiWhenClaudeFails() {
        AnthropicJobAnalysisService anthropic = mock(AnthropicJobAnalysisService.class);
        OpenAiJobAnalysisService openAi = mock(OpenAiJobAnalysisService.class);
        MockJobAnalysisService mockService = mock(MockJobAnalysisService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.analyze(command, "src")).thenThrow(new RuntimeException("claude down"));
        when(openAi.analyze(command, "src")).thenReturn(tagged("gpt-5"));

        JobAnalysisPayload result = provider(anthropic, openAi, mockService).analyze(command, "src");

        assertThat(result.usage().model()).isEqualTo("gpt-5");
        verify(mockService, never()).analyze(command, "src");
    }

    @Test
    void fallsBackToMockWhenOpenAiFails() {
        AnthropicJobAnalysisService anthropic = mock(AnthropicJobAnalysisService.class);
        OpenAiJobAnalysisService openAi = mock(OpenAiJobAnalysisService.class);
        MockJobAnalysisService mockService = mock(MockJobAnalysisService.class);
        when(anthropic.configured()).thenReturn(false); // Claude 키 없음 → 건너뜀
        when(openAi.analyze(command, "src")).thenThrow(new RuntimeException("openai down"));
        when(mockService.analyze(command, "src")).thenReturn(tagged("mock"));

        JobAnalysisPayload result = provider(anthropic, openAi, mockService).analyze(command, "src");

        assertThat(result.usage().model()).isEqualTo("mock");
    }

    @Test
    void usesOpenAiWhenClaudeNotConfigured() {
        AnthropicJobAnalysisService anthropic = mock(AnthropicJobAnalysisService.class);
        OpenAiJobAnalysisService openAi = mock(OpenAiJobAnalysisService.class);
        MockJobAnalysisService mockService = mock(MockJobAnalysisService.class);
        when(anthropic.configured()).thenReturn(false);
        when(openAi.analyze(command, "src")).thenReturn(tagged("gpt-5"));

        JobAnalysisPayload result = provider(anthropic, openAi, mockService).analyze(command, "src");

        assertThat(result.usage().model()).isEqualTo("gpt-5");
        verify(anthropic, never()).analyze(command, "src");
        verify(mockService, never()).analyze(command, "src");
    }
}
