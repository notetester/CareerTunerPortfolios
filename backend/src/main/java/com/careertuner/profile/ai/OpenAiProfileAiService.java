package com.careertuner.profile.ai;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.profile.ai.prompt.ProfilePromptCatalog;
import com.careertuner.profile.domain.UserProfile;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 프로필 평가의 OpenAI 단계. 키가 있으면 실제 평가를, 없거나 실패하면 규칙기반({@link RuleBasedProfileAiService})
 * 으로 폴백한다.
 *
 * <p>활성 진입점(@Primary)은 {@link FallbackProfileAiService}(Claude→OpenAI)다. 이 서비스는 그 폴백 체인의
 * OpenAI 단계이며, 내부 규칙기반 폴백이 최종 안전망이다. 스키마는 {@link ProfileAiSchemaProvider}, 응답 파싱은
 * {@link ProfileAiJsonValidator} 를 Claude 단계와 공유한다.
 */
@Service
@RequiredArgsConstructor
public class OpenAiProfileAiService implements ProfileAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final RuleBasedProfileAiService ruleBasedService;
    private final JobFamilyWeightPolicy weightPolicy;
    private final ProfileAiJsonValidator validator;
    private final ProfileAiSchemaProvider schemaProvider;
    private final ObjectMapper objectMapper;

    @Override
    public ProfileAiResult evaluate(UserProfile profile, String featureType) {
        JobFamily jobFamily = JobFamily.classify(profile);
        Map<ScoreCriterion, Integer> weights = weightPolicy.weightsFor(jobFamily);
        if (!openAiClient.configured()) {
            return ruleBasedService.evaluate(profile, featureType);
        }

        try {
            StructuredResponse response = openAiClient.request(
                    ProfileAiSchemaProvider.SCHEMA_NAME,
                    schemaProvider.schema(),
                    ProfilePromptCatalog.SYSTEM_PROMPT,
                    ProfilePromptCatalog.userPrompt(featureType, jobFamily, weights, json(profile)));
            return validator.validate(featureType, profile, jobFamily, weights, response.payload(), response.usage());
        } catch (RuntimeException exception) {
            ProfileAiResult fallback = ruleBasedService.evaluate(profile, featureType);
            return new ProfileAiResult(
                    fallback.featureType(),
                    fallback.summary(),
                    fallback.extractedSkills(),
                    fallback.strengths(),
                    fallback.gaps(),
                    fallback.recommendations(),
                    fallback.completenessScore(),
                    fallback.jobFamily(),
                    fallback.criteria(),
                    new CareerAnalysisAiUsage("profile-rule-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage());
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return "{}";
        }
    }
}
