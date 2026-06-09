package com.careertuner.dashboard.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient;
import com.careertuner.analysis.ai.provider.CareerAnalysisOpenAiClient.StructuredResponse;
import com.careertuner.dashboard.ai.prompt.DashboardInsightPromptCatalog;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Primary
@Service
public class OpenAiDashboardInsightAiService implements DashboardInsightAiService {

    private final CareerAnalysisOpenAiClient openAiClient;
    private final MockDashboardInsightAiService mockService;
    private final ObjectMapper objectMapper;

    public OpenAiDashboardInsightAiService(CareerAnalysisOpenAiClient openAiClient,
                                           MockDashboardInsightAiService mockService,
                                           ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.mockService = mockService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DashboardInsightAiResult summarize(DashboardInsightAiCommand command) {
        if (!openAiClient.configured()) {
            return mockService.summarize(command);
        }
        try {
            StructuredResponse response = openAiClient.request(
                    "dashboard_insight",
                    schema(),
                    DashboardInsightPromptCatalog.SYSTEM_PROMPT,
                    DashboardInsightPromptCatalog.userPrompt(json(command)));
            JsonNode payload = response.payload();
            return new DashboardInsightAiResult(
                    text(payload.path("summary")),
                    response.usage(),
                    "SUCCESS",
                    null,
                    false);
        } catch (RuntimeException exception) {
            DashboardInsightAiResult fallback = mockService.summarize(command);
            return new DashboardInsightAiResult(
                    fallback.summary(),
                    new CareerAnalysisAiUsage("mock-fallback", 0, 0, 0, true),
                    "FALLBACK",
                    exception.getMessage(),
                    true);
        }
    }

    private Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", Map.of("type", "string"));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
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
