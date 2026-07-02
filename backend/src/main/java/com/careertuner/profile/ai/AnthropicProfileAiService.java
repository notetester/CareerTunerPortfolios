package com.careertuner.profile.ai;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAnthropicClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.profile.ai.prompt.ProfilePromptCatalog;
import com.careertuner.profile.domain.UserProfile;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 프로필 평가의 Claude(Haiku) 단계 — 폴백 디스패처의 1차 폴백 provider.
 *
 * <p>{@link OpenAiProfileAiService} 와 같은 스키마({@link ProfileAiSchemaProvider})·파싱
 * ({@link ProfileAiJsonValidator})을 쓰되 전송만 {@link CareerAnalysisAnthropicClient} 로 바꾼 형태다.
 * 키가 없거나 호출/검증이 실패하면 예외를 던지고, 상위 {@link FallbackProfileAiService} 가 OpenAI 단계로 폴백한다.
 */
@Service
@RequiredArgsConstructor
public class AnthropicProfileAiService implements ProfileAiService {

    private final CareerAnalysisAnthropicClient anthropicClient;
    private final JobFamilyWeightPolicy weightPolicy;
    private final ProfileAiJsonValidator validator;
    private final ProfileAiSchemaProvider schemaProvider;
    private final ObjectMapper objectMapper;

    public boolean configured() {
        return anthropicClient.configured();
    }

    @Override
    public ProfileAiResult evaluate(UserProfile profile, String featureType) {
        JobFamily jobFamily = JobFamily.classify(profile);
        Map<ScoreCriterion, Integer> weights = weightPolicy.weightsFor(jobFamily);
        StructuredResponse response = anthropicClient.request(
                ProfileAiSchemaProvider.SCHEMA_NAME,
                schemaProvider.schema(),
                ProfilePromptCatalog.SYSTEM_PROMPT,
                ProfilePromptCatalog.userPrompt(featureType, jobFamily, weights, json(profile)));
        return validator.validate(featureType, profile, jobFamily, weights, response.payload(), response.usage());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return "{}";
        }
    }
}
