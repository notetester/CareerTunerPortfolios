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
                payload.path("strategy").asText(""),
                response.usage());
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) {
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

    private Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("fitScore", Map.of("type", "integer"));
        properties.put("matchedSkills", stringArray());
        properties.put("missingSkills", stringArray());
        properties.put("recommendedStudy", stringArray());
        properties.put("recommendedCertificates", stringArray());
        properties.put("strategy", Map.of("type", "string"));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    private Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }
}
