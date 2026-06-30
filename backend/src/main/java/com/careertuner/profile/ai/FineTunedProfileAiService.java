package com.careertuner.profile.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.profile.domain.UserProfile;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Primary
@Service
public class FineTunedProfileAiService implements ProfileAiService {

    private final FineTunedProfileAiProperties properties;
    private final OpenAiProfileAiService fallbackService;
    private final JobFamilyWeightPolicy weightPolicy;
    private final ProfileAiJsonValidator validator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FineTunedProfileAiService(FineTunedProfileAiProperties properties,
                                     OpenAiProfileAiService fallbackService,
                                     JobFamilyWeightPolicy weightPolicy,
                                     ProfileAiJsonValidator validator,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.fallbackService = fallbackService;
        this.weightPolicy = weightPolicy;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.httpClient = createHttpClient(properties);
    }

    @Override
    public ProfileAiResult evaluate(UserProfile profile, String featureType) {
        if (!properties.configured()) {
            return fallbackService.evaluate(profile, featureType);
        }

        JobFamily jobFamily = JobFamily.classify(profile);
        try {
            JsonNode payload = requestModelServer(profile, featureType, jobFamily);
            return validator.validate(
                    featureType,
                    jobFamily,
                    weightPolicy.weightsFor(jobFamily),
                    payload,
                    usage(profile, payload));
        } catch (RuntimeException exception) {
            log.warn("자체 프로필 AI 호출 실패, fallback으로 전환합니다: {}", exception.toString());
            ProfileAiResult fallback = fallbackService.evaluate(profile, featureType);
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
                    new CareerAnalysisAiUsage(properties.getModel() + " -> " + fallback.usage().model(), 0, 0, 0, fallback.usage().mock()),
                    "FALLBACK",
                    exception.getMessage());
        }
    }

    private JsonNode requestModelServer(UserProfile profile, String featureType, JobFamily jobFamily) {
        Map<String, Object> request = Map.of(
                "featureType", featureType,
                "jobFamily", jobFamily.name(),
                "profile", profile);
        try {
            String requestBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/analyze-profile"))
                    .timeout(properties.getTimeout())
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody.getBytes(StandardCharsets.UTF_8)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("자체 모델 서버 호출 실패: HTTP " + response.statusCode() + " - " + response.body());
            }
            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("자체 모델 서버 응답이 비어 있습니다.");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode result = root.path("result");
            if (!result.isObject()) {
                throw new IllegalStateException("자체 모델 서버 응답에 result 객체가 없습니다.");
            }
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("자체 모델 서버 응답 JSON 파싱에 실패했습니다.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("자체 모델 서버 통신에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("자체 모델 서버 호출이 중단되었습니다.", exception);
        }
    }

    private CareerAnalysisAiUsage usage(UserProfile profile, JsonNode payload) {
        int inputTokens = estimateTokens(json(profile));
        int outputTokens = estimateTokens(payload.toString());
        return new CareerAnalysisAiUsage(
                properties.getModel(),
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                false);
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, value.length() / 4);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return "";
        }
    }

    private HttpClient createHttpClient(FineTunedProfileAiProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }
}
