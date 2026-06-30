package com.careertuner.profile.ai;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class JobFamilyWeightPolicy {

    public Map<ScoreCriterion, Integer> weightsFor(JobFamily family) {
        return switch (family) {
            case DEVELOPMENT_DATA -> weights(10, 15, 20, 30, 15, 10);
            case SALES_MARKETING -> weights(15, 20, 25, 20, 10, 10);
            case DESIGN_CONTENT -> weights(10, 20, 20, 25, 15, 10);
            case BUSINESS_OFFICE -> weights(15, 20, 20, 15, 20, 10);
            case HEALTHCARE_SERVICE -> weights(15, 25, 20, 20, 10, 10);
            case EDUCATION_PUBLIC -> weights(15, 25, 15, 20, 15, 10);
            case PRODUCTION_LOGISTICS -> weights(10, 25, 25, 20, 10, 10);
            case ENGINEERING_TECHNICAL -> weights(15, 20, 20, 25, 10, 10);
            case GENERAL -> weights(15, 20, 20, 20, 15, 10);
        };
    }

    public List<Map<String, Object>> adminWeightProfiles() {
        return List.of(JobFamily.values()).stream()
                .map(family -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("jobFamily", family.name());
                    row.put("label", family.label());
                    row.put("description", family.description());
                    row.put("weights", displayWeights(weightsFor(family)));
                    return row;
                })
                .toList();
    }

    public List<Map<String, Object>> adminCriteria() {
        return List.of(ScoreCriterion.values()).stream()
                .map(criterion -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("criterion", criterion.name());
                    row.put("label", criterion.label());
                    row.put("description", criterion.description());
                    return row;
                })
                .toList();
    }

    private Map<ScoreCriterion, Integer> weights(int goal,
                                                 int experience,
                                                 int achievement,
                                                 int skill,
                                                 int document,
                                                 int improvement) {
        Map<ScoreCriterion, Integer> weights = new EnumMap<>(ScoreCriterion.class);
        weights.put(ScoreCriterion.GOAL_CLARITY, goal);
        weights.put(ScoreCriterion.EXPERIENCE_SPECIFICITY, experience);
        weights.put(ScoreCriterion.ACHIEVEMENT_EVIDENCE, achievement);
        weights.put(ScoreCriterion.JOB_SKILL_ALIGNMENT, skill);
        weights.put(ScoreCriterion.DOCUMENT_CONSISTENCY, document);
        weights.put(ScoreCriterion.IMPROVEMENT_READINESS, improvement);
        return weights;
    }

    private Map<String, Integer> displayWeights(Map<ScoreCriterion, Integer> weights) {
        Map<String, Integer> display = new LinkedHashMap<>();
        for (ScoreCriterion criterion : ScoreCriterion.values()) {
            display.put(criterion.name(), weights.getOrDefault(criterion, 0));
        }
        return display;
    }
}
