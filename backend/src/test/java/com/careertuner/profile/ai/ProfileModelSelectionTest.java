package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.profile.domain.UserProfile;

import tools.jackson.databind.ObjectMapper;

/** A 프로필 모델 선택 라우팅: Fallback tier 재정렬 + FineTuned 진입 라우팅(자체 skip). */
class ProfileModelSelectionTest {

    private final UserProfile profile = UserProfile.builder().desiredJob("사무").build();

    private ProfileAiResult tagged(String model) {
        return new ProfileAiResult(
                "PROFILE_SUMMARY", "요약", List.of("Excel"), List.of("강점"), List.of("보완점"), List.of("추천"),
                70, JobFamily.BUSINESS_OFFICE,
                List.of(new ProfileCriterionScore(ScoreCriterion.DOCUMENT_CONSISTENCY, 70, 20, 14.0, "근거", "개선")),
                new CareerAnalysisAiUsage(model, 0, 0, 0, false), "SUCCESS", null);
    }

    @Test
    void fallbackOpenAiChoiceSkipsClaude() {
        AnthropicProfileAiService anthropic = mock(AnthropicProfileAiService.class);
        OpenAiProfileAiService openAi = mock(OpenAiProfileAiService.class);
        when(openAi.evaluate(profile, "PROFILE_SUMMARY")).thenReturn(tagged("gpt-5"));

        FallbackProfileAiService service = new FallbackProfileAiService(anthropic, openAi);
        ProfileAiResult result = service.evaluate(profile, "PROFILE_SUMMARY", RequestedAiModel.OPENAI);

        assertThat(result.usage().model()).isEqualTo("gpt-5");
        verify(anthropic, never()).evaluate(any(), any());
        verify(anthropic, never()).configured();
    }

    @Test
    void fallbackClaudeChoiceUsesAnthropicWhenConfigured() {
        AnthropicProfileAiService anthropic = mock(AnthropicProfileAiService.class);
        OpenAiProfileAiService openAi = mock(OpenAiProfileAiService.class);
        when(anthropic.configured()).thenReturn(true);
        when(anthropic.evaluate(profile, "PROFILE_SUMMARY")).thenReturn(tagged("claude-haiku"));

        FallbackProfileAiService service = new FallbackProfileAiService(anthropic, openAi);
        ProfileAiResult result = service.evaluate(profile, "PROFILE_SUMMARY", RequestedAiModel.CLAUDE);

        assertThat(result.usage().model()).isEqualTo("claude-haiku");
        verify(openAi, never()).evaluate(any(), any());
    }

    @Test
    void fineTunedClaudeChoiceDelegatesToFallbackSkippingSelfModelServer() {
        FineTunedProfileAiProperties properties = mock(FineTunedProfileAiProperties.class);
        when(properties.getTimeout()).thenReturn(Duration.ofSeconds(5));
        FallbackProfileAiService fallback = mock(FallbackProfileAiService.class);
        when(fallback.evaluate(eq(profile), eq("PROFILE_SUMMARY"), any())).thenReturn(tagged("gpt-5"));

        FineTunedProfileAiService service = new FineTunedProfileAiService(
                properties, fallback, mock(JobFamilyWeightPolicy.class),
                mock(ProfileAiJsonValidator.class), mock(ObjectMapper.class));

        ProfileAiResult result = service.evaluate(profile, "PROFILE_SUMMARY", RequestedAiModel.CLAUDE);

        assertThat(result.usage().model()).isEqualTo("gpt-5");
        // 자체 모델 서버 미접촉(명시 CLAUDE 는 자체 tier 를 건너뛴다).
        verify(properties, never()).getBaseUrl();
    }
}
