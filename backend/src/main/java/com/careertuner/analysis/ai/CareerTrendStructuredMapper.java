package com.careertuner.analysis.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.analysis.ai.prompt.CareerTrendPromptCatalog;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 커리어 트렌드 구조화 출력의 스키마·프롬프트·응답 파싱을 모은 매퍼.
 *
 * <p>전송 provider(OpenAI/Claude)와 무관한 도메인 변환 로직이라 {@link OpenAiCareerTrendAiService} 와
 * {@link AnthropicCareerTrendAiService} 가 공유한다(스키마·파싱 중복 방지).
 */
@Component
public class CareerTrendStructuredMapper {

    /** OpenAI json_schema 의 name. (Anthropic 은 사용하지 않음) */
    public static final String SCHEMA_NAME = "career_trend";

    private final ObjectMapper objectMapper;

    public CareerTrendStructuredMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> schema() {
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

    public String userPrompt(CareerTrendAiCommand command) {
        return CareerTrendPromptCatalog.userPrompt(json(command));
    }

    public CareerTrendAiResult toResult(JsonNode payload, CareerAnalysisAiUsage usage) {
        return new CareerTrendAiResult(
                text(payload.path("trendSummary")),
                strings(payload.path("recommendedDirections")),
                usage,
                "SUCCESS",
                null,
                false);
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
