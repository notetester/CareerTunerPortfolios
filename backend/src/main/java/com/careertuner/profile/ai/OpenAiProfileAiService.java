package com.careertuner.profile.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.profile.ai.prompt.ProfilePromptCatalog;
import com.careertuner.profile.domain.UserProfile;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Primary
@Service
@RequiredArgsConstructor
public class OpenAiProfileAiService implements ProfileAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final RuleBasedProfileAiService ruleBasedService;
    private final JobFamilyWeightPolicy weightPolicy;
    private final ProfileAiJsonValidator validator;
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
                    "profile_evaluation",
                    schema(),
                    ProfilePromptCatalog.SYSTEM_PROMPT,
                    ProfilePromptCatalog.userPrompt(featureType, jobFamily, weights, json(profile)));
            return validator.validate(featureType, jobFamily, weights, response.payload(), response.usage());
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

    private Map<String, Object> schema() {
        Map<String, Object> criterionScore = new LinkedHashMap<>();
        criterionScore.put("criterion", enumString(List.of(ScoreCriterion.values()).stream().map(Enum::name).toList()));
        criterionScore.put("rawScore", Map.of("type", "integer", "minimum", 0, "maximum", 100));
        criterionScore.put("evidence", string());
        criterionScore.put("improvement", string());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", string());
        properties.put("extractedSkills", stringArray());
        properties.put("strengths", stringArray());
        properties.put("gaps", stringArray());
        properties.put("recommendations", stringArray());
        properties.put("criterionScores", Map.of(
                "type", "array",
                "items", objectSchema(criterionScore)));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    private Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", string());
    }

    private Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private Map<String, Object> enumString(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return "{}";
        }
    }
}
