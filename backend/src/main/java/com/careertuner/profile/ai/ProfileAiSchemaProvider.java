package com.careertuner.profile.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 프로필 평가 구조화 출력의 JSON 스키마 제공자.
 *
 * <p>OpenAI/Claude 단계가 동일 스키마를 쓰도록 추출했다(중복 방지). 응답 파싱은 도메인 규칙이 많아
 * 기존 {@link ProfileAiJsonValidator} 가 담당하므로, 여기서는 스키마만 공유한다.
 */
@Component
public class ProfileAiSchemaProvider {

    /** OpenAI json_schema 의 name. (Anthropic 은 사용하지 않음) */
    public static final String SCHEMA_NAME = "profile_evaluation";

    public Map<String, Object> schema() {
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
}
