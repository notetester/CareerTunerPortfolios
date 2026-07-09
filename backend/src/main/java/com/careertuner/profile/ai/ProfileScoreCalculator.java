package com.careertuner.profile.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ProfileScoreCalculator {

    public List<ProfileCriterionScore> applyWeights(Map<ScoreCriterion, Integer> weights,
                                                    Map<ScoreCriterion, Integer> rawScores,
                                                    Map<ScoreCriterion, String> evidence,
                                                    Map<ScoreCriterion, String> improvement) {
        List<ProfileCriterionScore> criteria = new ArrayList<>();
        for (ScoreCriterion criterion : ScoreCriterion.values()) {
            int weight = weights.getOrDefault(criterion, 0);
            int rawScore = clamp(rawScores.getOrDefault(criterion, 0));
            double weightedScore = Math.round(rawScore * weight) / 100.0;
            criteria.add(new ProfileCriterionScore(
                    criterion,
                    rawScore,
                    weight,
                    weightedScore,
                    evidence.getOrDefault(criterion, ""),
                    improvement.getOrDefault(criterion, "")));
        }
        return criteria;
    }

    public int totalScore(List<ProfileCriterionScore> criteria) {
        double total = criteria.stream().mapToDouble(ProfileCriterionScore::weightedScore).sum();
        return clamp((int) Math.round(total));
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
