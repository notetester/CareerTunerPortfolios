package com.careertuner.analysis.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.analysis.ai.prompt.CareerTrendPromptCatalog;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Primary
@Service
public class OpenAiCareerTrendAiService implements CareerTrendAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final MockCareerTrendAiService mockService;
    private final ObjectMapper objectMapper;

    public OpenAiCareerTrendAiService(CareerAnalysisOpenAiClient openAiClient,
                                      MockCareerTrendAiService mockService,
                                      ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.mockService = mockService;
        this.objectMapper = objectMapper;
    }

    @Override
    public CareerTrendAiResult generate(CareerTrendAiCommand command) {
        if (!openAiClient.configured()) {
            return mockService.generate(command);
        }
        try {
            StructuredResponse response = openAiClient.request(
                    "career_trend",
                    schema(),
                    CareerTrendPromptCatalog.SYSTEM_PROMPT,
                    CareerTrendPromptCatalog.userPrompt(json(command)));
            JsonNode payload = response.payload();
            return new CareerTrendAiResult(
                    text(payload.path("trendSummary")),
                    strings(payload.path("recommendedDirections")),
                    response.usage(),
                    "SUCCESS",
                    null,
                    false);
        } catch (RuntimeException exception) {
            CareerTrendAiResult fallback = mockService.generate(command);
            return new CareerTrendAiResult(
                    fallback.trendSummary(),
                    fallback.recommendedDirections(),
                    new CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage(),
                    true);
        }
    }

    private Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("trendSummary", Map.of("type", "string"));
        properties.put("recommendedDirections", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            return "{}";
        }
    }
}
