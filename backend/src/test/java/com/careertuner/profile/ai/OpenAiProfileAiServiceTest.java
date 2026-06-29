package com.careertuner.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.profile.domain.UserProfile;

import tools.jackson.databind.ObjectMapper;

class OpenAiProfileAiServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesRuleBasedServiceWhenOpenAiIsNotConfigured() {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        RuleBasedProfileAiService ruleBasedService = mock(RuleBasedProfileAiService.class);
        when(client.configured()).thenReturn(false);
        when(ruleBasedService.evaluate(any(), anyString())).thenReturn(fallback("SUCCESS", "profile-rule-v2"));
        OpenAiProfileAiService service = service(client, ruleBasedService);

        ProfileAiResult result = service.evaluate(UserProfile.builder().desiredJob("사무").build(), "PROFILE_COMPLETENESS");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.usage().model()).isEqualTo("profile-rule-v2");
    }

    @Test
    void fallsBackWhenOpenAiJsonValidationFails() throws Exception {
        CareerAnalysisOpenAiClient client = mock(CareerAnalysisOpenAiClient.class);
        RuleBasedProfileAiService ruleBasedService = mock(RuleBasedProfileAiService.class);
        when(client.configured()).thenReturn(true);
        when(client.request(anyString(), any(), anyString(), anyString()))
                .thenReturn(new StructuredResponse(
                        objectMapper.readTree("{\"summary\":\"invalid\"}"),
                        new CareerAnalysisAiUsage("gpt-5", 10, 10, 20, false)));
        when(ruleBasedService.evaluate(any(), anyString())).thenReturn(fallback("SUCCESS", "profile-rule-v2"));
        OpenAiProfileAiService service = service(client, ruleBasedService);

        ProfileAiResult result = service.evaluate(UserProfile.builder().desiredJob("마케팅").build(), "PROFILE_SUMMARY");

        assertThat(result.status()).isEqualTo("FALLBACK");
        assertThat(result.usage().model()).isEqualTo("profile-rule-fallback");
        assertThat(result.errorMessage()).contains("criterionScores");
    }

    private OpenAiProfileAiService service(CareerAnalysisOpenAiClient client,
                                           RuleBasedProfileAiService ruleBasedService) {
        return new OpenAiProfileAiService(
                client,
                ruleBasedService,
                new JobFamilyWeightPolicy(),
                new ProfileAiJsonValidator(new ProfileScoreCalculator()),
                new ProfileAiSchemaProvider(),
                objectMapper);
    }

    private ProfileAiResult fallback(String status, String model) {
        return new ProfileAiResult(
                "PROFILE_SUMMARY",
                "요약",
                List.of("Excel"),
                List.of("강점"),
                List.of("보완점"),
                List.of("추천"),
                70,
                JobFamily.BUSINESS_OFFICE,
                List.of(new ProfileCriterionScore(
                        ScoreCriterion.DOCUMENT_CONSISTENCY,
                        70,
                        20,
                        14.0,
                        "근거",
                        "개선")),
                new CareerAnalysisAiUsage(model, 0, 0, 0, true),
                status,
                null);
    }
}
