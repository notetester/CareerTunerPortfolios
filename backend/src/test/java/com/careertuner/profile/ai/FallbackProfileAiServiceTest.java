package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.profile.domain.UserProfile;

/**
 * 프로필 평가 폴백 디스패처(Claude→OpenAI) 검증.
 */
class FallbackProfileAiServiceTest {

    private final UserProfile profile = UserProfile.builder().desiredJob("사무").build();

    private ProfileAiResult tagged(String model) {
        return new ProfileAiResult(
                "PROFILE_SUMMARY", "요약", List.of("Excel"), List.of("강점"), List.of("보완점"), List.of("추천"),
                70, JobFamily.BUSINESS_OFFICE,
                List.of(new ProfileCriterionScore(ScoreCriterion.DOCUMENT_CONSISTENCY, 70, 20, 14.0, "근거", "개선")),
                new CareerAnalysisAiUsage(model, 0, 0, 0, false), "SUCCESS", null);
    }

    @Test
    void usesClaudeWhenConfigured() {
        AnthropicProfileAiService anthropic = mock(AnthropicProfileAiService.class);
        OpenAiProfileAiService openAi = mock(OpenAiProfileAiService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.evaluate(profile, "PROFILE_SUMMARY")).thenReturn(tagged("claude-haiku"));

        FallbackProfileAiService service = new FallbackProfileAiService(anthropic, openAi);
        ProfileAiResult result = service.evaluate(profile, "PROFILE_SUMMARY");

        assertThat(result.usage().model()).isEqualTo("claude-haiku");
        verify(openAi, never()).evaluate(profile, "PROFILE_SUMMARY");
    }

    @Test
    void fallsBackToOpenAiWhenClaudeNotConfigured() {
        AnthropicProfileAiService anthropic = mock(AnthropicProfileAiService.class);
        OpenAiProfileAiService openAi = mock(OpenAiProfileAiService.class);
        when(anthropic.configured()).thenReturn(false);
        when(openAi.evaluate(profile, "PROFILE_SUMMARY")).thenReturn(tagged("gpt-5"));

        FallbackProfileAiService service = new FallbackProfileAiService(anthropic, openAi);
        ProfileAiResult result = service.evaluate(profile, "PROFILE_SUMMARY");

        assertThat(result.usage().model()).isEqualTo("gpt-5");
        verify(anthropic, never()).evaluate(profile, "PROFILE_SUMMARY");
    }

    @Test
    void fallsBackToOpenAiWhenClaudeFails() {
        AnthropicProfileAiService anthropic = mock(AnthropicProfileAiService.class);
        OpenAiProfileAiService openAi = mock(OpenAiProfileAiService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.evaluate(profile, "PROFILE_SUMMARY")).thenThrow(new IllegalStateException("claude down"));
        when(openAi.evaluate(profile, "PROFILE_SUMMARY")).thenReturn(tagged("gpt-5"));

        FallbackProfileAiService service = new FallbackProfileAiService(anthropic, openAi);
        ProfileAiResult result = service.evaluate(profile, "PROFILE_SUMMARY");

        assertThat(result.usage().model()).isEqualTo("gpt-5");
    }
}
