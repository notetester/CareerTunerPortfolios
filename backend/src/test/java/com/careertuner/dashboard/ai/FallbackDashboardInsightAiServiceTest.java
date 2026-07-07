package com.careertuner.dashboard.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiProviderProperties;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

/**
 * 대시보드 요약 폴백 디스패처(Claude→OpenAI) 검증.
 */
class FallbackDashboardInsightAiServiceTest {

    private final DashboardInsightAiCommand command = new DashboardInsightAiCommand(null, null, null);

    private DashboardInsightAiResult tagged(String model) {
        return new DashboardInsightAiResult("요약",
                new CareerAnalysisAiUsage(model, 0, 0, 0, false), "SUCCESS", null, false);
    }

    @Test
    void usesClaudeWhenConfigured() {
        AnthropicDashboardInsightAiService anthropic = mock(AnthropicDashboardInsightAiService.class);
        OpenAiDashboardInsightAiService openAi = mock(OpenAiDashboardInsightAiService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.summarize(command)).thenReturn(tagged("claude-haiku"));

        FallbackDashboardInsightAiService service = new FallbackDashboardInsightAiService(
                anthropic, openAi, mock(MockDashboardInsightAiService.class), new CareerAnalysisAiProviderProperties());
        DashboardInsightAiResult result = service.summarize(command);

        assertThat(result.usage().model()).isEqualTo("claude-haiku");
        verify(openAi, never()).summarize(command);
    }

    @Test
    void fallsBackToOpenAiWhenClaudeNotConfigured() {
        AnthropicDashboardInsightAiService anthropic = mock(AnthropicDashboardInsightAiService.class);
        OpenAiDashboardInsightAiService openAi = mock(OpenAiDashboardInsightAiService.class);
        when(anthropic.configured()).thenReturn(false);
        when(openAi.summarize(command)).thenReturn(tagged("gpt-5"));

        FallbackDashboardInsightAiService service = new FallbackDashboardInsightAiService(
                anthropic, openAi, mock(MockDashboardInsightAiService.class), new CareerAnalysisAiProviderProperties());
        DashboardInsightAiResult result = service.summarize(command);

        assertThat(result.usage().model()).isEqualTo("gpt-5");
        verify(anthropic, never()).summarize(command);
    }

    @Test
    void fallsBackToOpenAiWhenClaudeFails() {
        AnthropicDashboardInsightAiService anthropic = mock(AnthropicDashboardInsightAiService.class);
        OpenAiDashboardInsightAiService openAi = mock(OpenAiDashboardInsightAiService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.summarize(command)).thenThrow(new IllegalStateException("claude down"));
        when(openAi.summarize(command)).thenReturn(tagged("gpt-5"));

        FallbackDashboardInsightAiService service = new FallbackDashboardInsightAiService(
                anthropic, openAi, mock(MockDashboardInsightAiService.class), new CareerAnalysisAiProviderProperties());
        DashboardInsightAiResult result = service.summarize(command);

        assertThat(result.usage().model()).isEqualTo("gpt-5");
    }
}
