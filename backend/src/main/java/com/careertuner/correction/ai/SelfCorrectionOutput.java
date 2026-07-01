package com.careertuner.correction.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SelfCorrectionOutput(
        String status,
        String taskType,
        String correctedText,
        String summary,
        List<Change> changes,
        List<String> riskFlags,
        boolean preservedMeaning,
        List<String> addedFacts,
        List<String> recommendedKeywords,
        double confidence
) {

    public record Change(String before, String after, String reason, String evidenceSource) {
        Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("before", before);
            value.put("after", after);
            value.put("reason", reason);
            value.put("evidence_source", evidenceSource);
            return value;
        }
    }

    public Map<String, Object> toResultMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("task_type", taskType);
        result.put("corrected_text", correctedText);
        result.put("summary", summary);
        result.put("changes", changes.stream().map(Change::toMap).toList());
        result.put("risk_flags", riskFlags);
        result.put("preserved_meaning", preservedMeaning);
        result.put("added_facts", addedFacts);
        result.put("recommended_keywords", recommendedKeywords);
        result.put("confidence", confidence);
        return result;
    }
}
