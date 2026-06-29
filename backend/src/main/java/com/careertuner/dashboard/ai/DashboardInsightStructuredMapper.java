package com.careertuner.dashboard.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.dashboard.ai.prompt.DashboardInsightPromptCatalog;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 대시보드 요약 구조화 출력의 스키마·프롬프트·응답 파싱을 모은 매퍼.
 *
 * <p>전송 provider(OpenAI/Claude)와 무관한 도메인 변환 로직이라 {@link OpenAiDashboardInsightAiService} 와
 * {@link AnthropicDashboardInsightAiService} 가 공유한다.
 */
@Component
public class DashboardInsightStructuredMapper {

    /** OpenAI json_schema 의 name. (Anthropic 은 사용하지 않음) */
    public static final String SCHEMA_NAME = "dashboard_insight";

    private final ObjectMapper objectMapper;

    public DashboardInsightStructuredMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", Map.of("type", "string"));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    public String userPrompt(DashboardInsightAiCommand command) {
        return DashboardInsightPromptCatalog.userPrompt(json(command));
    }

    public DashboardInsightAiResult toResult(JsonNode payload, CareerAnalysisAiUsage usage) {
        return new DashboardInsightAiResult(
                text(payload.path("summary")),
                usage,
                "SUCCESS",
                null,
                false);
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
