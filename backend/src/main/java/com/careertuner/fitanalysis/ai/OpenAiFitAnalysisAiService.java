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
                    fallback.conditionMatrix(),
                    fallback.applyDecision(),
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
     * 실 AI 가 생성한 지원 판단을 mock 과 동일한 결정적 규칙(APPLY = 70점 이상 & 필수 미충족 0개)으로
     * 검증하는 가드레일. LLM 이 비교 매트릭스와 모순되게 APPLY 를 내면 COMPLEMENT 로 강등해
     * 모순된 판단이 사용자에게 노출되는 것을 사전 차단한다(관리자 REQUIRED_GAP_APPLY 검수 플래그의 예방 단계).
     * AI 가 제시한 reasons/actions 은 유지하고 보정 사유만 덧붙인다.
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
