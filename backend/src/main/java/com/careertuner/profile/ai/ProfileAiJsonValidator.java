package com.careertuner.profile.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class ProfileAiJsonValidator {

    private final ProfileScoreCalculator scoreCalculator;

    public ProfileAiResult validate(String featureType,
                                    JobFamily jobFamily,
                                    Map<ScoreCriterion, Integer> weights,
                                    JsonNode payload,
                                    CareerAnalysisAiUsage usage) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("AI 응답이 객체 JSON이 아닙니다.");
        }

        Map<ScoreCriterion, Integer> rawScores = new EnumMap<>(ScoreCriterion.class);
        Map<ScoreCriterion, String> evidence = new EnumMap<>(ScoreCriterion.class);
        Map<ScoreCriterion, String> improvement = new EnumMap<>(ScoreCriterion.class);
        JsonNode criterionScores = payload.path("criterionScores");
        if (!criterionScores.isArray()) {
            throw new IllegalArgumentException("criterionScores 배열이 없습니다.");
        }
        for (JsonNode item : criterionScores) {
            ScoreCriterion criterion = parseCriterion(item.path("criterion").asText(""));
            int rawScore = item.path("rawScore").asInt(-1);
            if (rawScore < 0 || rawScore > 100) {
                throw new IllegalArgumentException("평가 점수는 0~100 사이여야 합니다.");
            }
            rawScores.put(criterion, rawScore);
            evidence.put(criterion, text(item.path("evidence")));
            improvement.put(criterion, text(item.path("improvement")));
        }
        for (ScoreCriterion criterion : ScoreCriterion.values()) {
            if (!rawScores.containsKey(criterion)) {
                throw new IllegalArgumentException("누락된 평가 기준이 있습니다: " + criterion.name());
            }
        }

        List<ProfileCriterionScore> criteria = scoreCalculator.applyWeights(weights, rawScores, evidence, improvement);
        return new ProfileAiResult(
                featureType,
                text(payload.path("summary")),
                strings(payload.path("extractedSkills")),
                strings(payload.path("strengths")),
                strings(payload.path("gaps")),
                strings(payload.path("recommendations")),
                scoreCalculator.totalScore(criteria),
                jobFamily,
                criteria,
                usage,
                "SUCCESS",
                null);
    }

    private ScoreCriterion parseCriterion(String value) {
        try {
            return ScoreCriterion.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("알 수 없는 평가 기준입니다: " + value);
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
        return node == null ? "" : node.asText("").trim();
    }
}
