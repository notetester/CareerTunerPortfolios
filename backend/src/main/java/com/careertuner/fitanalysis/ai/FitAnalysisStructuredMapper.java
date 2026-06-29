package com.careertuner.fitanalysis.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

import tools.jackson.databind.JsonNode;

/**
 * 적합도 분석 구조화 출력의 JSON 스키마와 응답 파싱을 한곳에 모은 매퍼.
 *
 * <p>전송 provider(OpenAI/Claude)와 무관한 도메인 변환 로직이므로, {@link OpenAiFitAnalysisAiService}
 * 와 {@link AnthropicFitAnalysisAiService} 가 이 매퍼를 공유한다(스키마·파싱 중복 방지).
 */
@Component
public class FitAnalysisStructuredMapper {

    /** OpenAI json_schema 의 name. (Anthropic 은 사용하지 않음) */
    public static final String SCHEMA_NAME = "fit_analysis";

    public Map<String, Object> schema() {
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
        properties.put("conditionMatrix", Map.of(
                "type", "array",
                "items", objectSchema(Map.of(
                        "condition", string(),
                        "conditionType", enumString("REQUIRED", "PREFERRED"),
                        "matchStatus", enumString("MET", "PARTIAL", "UNMET"),
                        "evidence", string()))));
        properties.put("applyDecision", objectSchema(Map.of(
                "decision", enumString("APPLY", "COMPLEMENT", "HOLD"),
                "reasons", stringArray(),
                "actions", stringArray())));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    /** 구조화 응답 payload + 사용량을 도메인 결과(SUCCESS)로 변환한다. */
    public FitAnalysisAiResult toResult(JsonNode payload, CareerAnalysisAiUsage usage) {
        int fitScore = Math.max(0, Math.min(100, payload.path("fitScore").asInt(0)));
        List<FitConditionMatch> conditionMatrix = conditionMatrix(payload.path("conditionMatrix"));
        FitApplyDecision applyDecision =
                guardApplyDecision(fitScore, conditionMatrix, applyDecision(payload.path("applyDecision")));
        return new FitAnalysisAiResult(
                fitScore,
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
                conditionMatrix,
                applyDecision,
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

    private List<FitConditionMatch> conditionMatrix(JsonNode node) {
        List<FitConditionMatch> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                values.add(new FitConditionMatch(
                        item.path("condition").asText(""),
                        item.path("conditionType").asText("REQUIRED"),
                        item.path("matchStatus").asText("UNMET"),
                        item.path("evidence").asText("")));
            }
        }
        return values;
    }

    private FitApplyDecision applyDecision(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return new FitApplyDecision("COMPLEMENT", List.of(), List.of());
        }
        return new FitApplyDecision(
                node.path("decision").asText("COMPLEMENT"),
                strings(node.path("reasons")),
                strings(node.path("actions")));
    }

    /**
     * 실 AI 가 생성한 지원 판단을 mock 과 동일한 결정적 규칙(APPLY = 70점 이상 &amp; 필수 미충족 0개)으로
     * 검증하는 가드레일. LLM 이 비교 매트릭스와 모순되게 APPLY 를 내면 COMPLEMENT 로 강등한다.
     */
    private FitApplyDecision guardApplyDecision(int fitScore,
                                                List<FitConditionMatch> conditionMatrix,
                                                FitApplyDecision decision) {
        if (!"APPLY".equals(decision.decision())) {
            return decision;
        }
        long requiredUnmet = conditionMatrix.stream()
                .filter(row -> "REQUIRED".equals(row.conditionType()) && "UNMET".equals(row.matchStatus()))
                .count();
        if (fitScore >= 70 && requiredUnmet == 0) {
            return decision;
        }
        List<String> reasons = new ArrayList<>(decision.reasons());
        reasons.add("자동 보정: 적합도 %d점·필수 미충족 %d개 기준에 따라 '보완 후 지원'으로 조정했습니다."
                .formatted(fitScore, requiredUnmet));
        return new FitApplyDecision("COMPLEMENT", reasons, decision.actions());
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
