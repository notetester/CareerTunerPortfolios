package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.fitanalysis.ai.prompt.FitAnalysisPromptCatalog;

import tools.jackson.databind.JsonNode;

/**
 * API 키가 있으면 실제 구조화 AI 분석을 실행하고, 없으면 결정적 mock으로 전체 흐름을 유지한다.
 */
@Primary
@Service
public class OpenAiFitAnalysisAiService implements FitAnalysisAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final MockFitAnalysisAiService mockService;

    public OpenAiFitAnalysisAiService(CareerAnalysisOpenAiClient openAiClient, MockFitAnalysisAiService mockService) {
        this.openAiClient = openAiClient;
        this.mockService = mockService;
    }

    @Override
    public FitAnalysisAiResult generate(FitAnalysisAiCommand command) {
        if (!openAiClient.configured()) {
            return mockService.generate(command);
        }

        try {
            StructuredResponse response = openAiClient.request(
                    "fit_analysis",
                    schema(),
                    FitAnalysisPromptCatalog.SYSTEM_PROMPT,
                    FitAnalysisPromptCatalog.userPrompt(
                            command.companyName(),
                            command.jobTitle(),
                            String.join(", ", command.requiredSkills()),
                            String.join(", ", command.preferredSkills()),
                            command.duties(),
                            String.join(", ", command.profileSkills()),
                            String.join(", ", command.profileCertificates()),
                            command.desiredJob()));

            JsonNode payload = response.payload();
            return new FitAnalysisAiResult(
                    Math.max(0, Math.min(100, payload.path("fitScore").asInt(0))),
                    strings(payload.path("matchedSkills")),
                    strings(payload.path("missingSkills")),
                    strings(payload.path("recommendedStudy")),
                    strings(payload.path("recommendedCertificates")),
                    text(payload.path("strategy")),
                    strings(payload.path("scoreBasis")),
                    gaps(payload.path("gapRecommendations")),
                    roadmap(payload.path("learningRoadmap")),
                    certificates(payload.path("certificateRecommendations")),
                    strings(payload.path("strategyActions")),
                    response.usage(),
                    "SUCCESS",
                    null,
                    false);
        } catch (RuntimeException exception) {
            FitAnalysisAiResult fallback = mockService.generate(command);
            return new FitAnalysisAiResult(
                    fallback.fitScore(),
                    fallback.matchedSkills(),
                    fallback.missingSkills(),
                    fallback.recommendedStudy(),
                    fallback.recommendedCertificates(),
                    fallback.strategy(),
                    fallback.scoreBasis(),
                    fallback.gapRecommendations(),
                    fallback.learningRoadmap(),
                    fallback.certificateRecommendations(),
                    fallback.strategyActions(),
                    new com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage(),
                    true);
        }
    }

    private List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String text(JsonNode node) {
        return node == null ? "" : node.asText("");
    }

    private Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("fitScore", Map.of("type", "integer"));
        properties.put("matchedSkills", stringArray());
        properties.put("missingSkills", stringArray());
        properties.put("recommendedStudy", stringArray());
        properties.put("recommendedCertificates", stringArray());
        properties.put("strategy", Map.of("type", "string"));
        properties.put("scoreBasis", stringArray());
        properties.put("gapRecommendations", Map.of(
                "type", "array",
                "items", objectSchema(Map.of(
                        "skill", string(),
                        "category", enumString("REQUIRED_MISSING", "PREFERRED_GAP", "LONG_TERM_GROWTH"),
                        "priority", enumString("HIGH", "MEDIUM", "LOW"),
                        "reason", string()))));
        properties.put("learningRoadmap", Map.of(
                "type", "array",
                "items", objectSchema(Map.of(
                        "skill", string(),
                        "title", string(),
                        "practiceTask", string(),
                        "expectedDuration", string(),
                        "priority", enumString("HIGH", "MEDIUM", "LOW"),
                        "sortOrder", Map.of("type", "integer")))));
        properties.put("certificateRecommendations", Map.of(
                "type", "array",
                "items", objectSchema(Map.of(
                        "name", string(),
                        "priority", enumString("HIGH", "MEDIUM", "LOW"),
                        "reason", string()))));
        properties.put("strategyActions", stringArray());
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

    private Map<String, Object> enumString(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    private List<FitGapRecommendation> gaps(JsonNode node) {
        List<FitGapRecommendation> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                values.add(new FitGapRecommendation(
                        item.path("skill").asText(""),
                        item.path("category").asText("LONG_TERM_GROWTH"),
                        item.path("priority").asText("LOW"),
                        item.path("reason").asText("")));
            }
        }
        return values;
    }

    private List<FitLearningRoadmapItem> roadmap(JsonNode node) {
        List<FitLearningRoadmapItem> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                values.add(new FitLearningRoadmapItem(
                        item.path("skill").asText(""),
                        item.path("title").asText(""),
                        item.path("practiceTask").asText(""),
                        item.path("expectedDuration").asText(""),
                        item.path("priority").asText("MEDIUM"),
                        item.path("sortOrder").asInt(values.size() + 1)));
            }
        }
        return values;
    }

    private List<FitCertificateRecommendation> certificates(JsonNode node) {
        List<FitCertificateRecommendation> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                values.add(new FitCertificateRecommendation(
                        item.path("name").asText(""),
                        item.path("priority").asText("LOW"),
                        item.path("reason").asText("")));
            }
        }
        return values;
    }
}
