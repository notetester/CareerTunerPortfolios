package com.careertuner.analysis.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

/**
 * 커리어 트렌드 폴백 디스패처(Claude→OpenAI) 검증.
 */
class FallbackCareerTrendAiServiceTest {

    private final CareerTrendAiCommand command = new CareerTrendAiCommand(null, null, null, null, null, null);

    private CareerTrendAiResult tagged(String model) {
        return new CareerTrendAiResult("요약", List.of("방향"),
                new CareerAnalysisAiUsage(model, 0, 0, 0, false), "SUCCESS", null, false);
    }

    @Test
    void usesClaudeWhenConfigured() {
        AnthropicCareerTrendAiService anthropic = mock(AnthropicCareerTrendAiService.class);
        OpenAiCareerTrendAiService openAi = mock(OpenAiCareerTrendAiService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(command)).thenReturn(tagged("claude-haiku"));

        FallbackCareerTrendAiService service = new FallbackCareerTrendAiService(anthropic, openAi);
        CareerTrendAiResult result = service.generate(command);

        assertThat(result.usage().model()).isEqualTo("claude-haiku");
        verify(openAi, never()).generate(command);
    }

    @Test
    void fallsBackToOpenAiWhenClaudeNotConfigured() {
        AnthropicCareerTrendAiService anthropic = mock(AnthropicCareerTrendAiService.class);
        OpenAiCareerTrendAiService openAi = mock(OpenAiCareerTrendAiService.class);
        when(anthropic.configured()).thenReturn(false);
        when(openAi.generate(command)).thenReturn(tagged("gpt-5"));

        FallbackCareerTrendAiService service = new FallbackCareerTrendAiService(anthropic, openAi);
        CareerTrendAiResult result = service.generate(command);

        assertThat(result.usage().model()).isEqualTo("gpt-5");
        verify(anthropic, never()).generate(command);
    }

    @Test
    void fallsBackToOpenAiWhenClaudeFails() {
        AnthropicCareerTrendAiService anthropic = mock(AnthropicCareerTrendAiService.class);
        OpenAiCareerTrendAiService openAi = mock(OpenAiCareerTrendAiService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.generate(command)).thenThrow(new IllegalStateException("claude down"));
        when(openAi.generate(command)).thenReturn(tagged("gpt-5"));

        FallbackCareerTrendAiService service = new FallbackCareerTrendAiService(anthropic, openAi);
        CareerTrendAiResult result = service.generate(command);

        assertThat(result.usage().model()).isEqualTo("gpt-5");
    }
}
