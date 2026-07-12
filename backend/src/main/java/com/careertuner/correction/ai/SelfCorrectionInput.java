package com.careertuner.correction.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.careertuner.correction.ai.CorrectionAiClient.CorrectionCommand;

public record SelfCorrectionInput(
        String id,
        String taskType,
        String originalText,
        String targetRole,
        Map<String, Object> jobContext,
        List<String> userProfileFacts,
        Map<String, Object> constraints,
        Map<String, Object> sourceProvenance
) {

    /** 기존 학습/테스트 호출 호환. provenance는 모델 입력이 아니라 저장용이므로 생략 가능하다. */
    public SelfCorrectionInput(
            String id,
            String taskType,
            String originalText,
            String targetRole,
            Map<String, Object> jobContext,
            List<String> userProfileFacts,
            Map<String, Object> constraints
    ) {
        this(id, taskType, originalText, targetRole, jobContext, userProfileFacts, constraints, Map.of());
    }

    public Map<String, Object> toRequestMap() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("original_text", originalText);
        input.put("target_role", targetRole);
        input.put("job_context", jobContext == null ? Map.of() : jobContext);
        input.put("user_profile_facts", userProfileFacts == null ? List.of() : userProfileFacts);
        input.put("constraints", constraints == null ? Map.of() : constraints);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", id);
        request.put("task_type", taskType);
        request.put("input", input);
        return request;
    }

    public static SelfCorrectionInput minimal(CorrectionCommand command) {
        String original = command.originalText() == null ? "" : command.originalText();
        String taskType = taskType(command.correctionType());
        return new SelfCorrectionInput(
                "runtime-" + UUID.randomUUID(),
                taskType,
                original,
                command.applicationCase() == null ? "" : safe(command.applicationCase().getJobTitle()),
                Map.of(),
                List.of(),
                defaultConstraints(taskType, original),
                Map.of());
    }

    public static Map<String, Object> defaultConstraints(String taskType, String originalText) {
        String original = originalText == null ? "" : originalText;
        int minPercent;
        int maxPercent;
        boolean preserveParagraphs;
        switch (taskType == null ? "" : taskType) {
            case "SELF_INTRO_CORRECTION" -> {
                minPercent = 85;
                maxPercent = 110;
                preserveParagraphs = true;
            }
            case "INTERVIEW_ANSWER_CORRECTION" -> {
                minPercent = 90;
                maxPercent = 125;
                preserveParagraphs = false;
            }
            case "RESUME_EXPRESSION_IMPROVEMENT" -> {
                minPercent = 80;
                maxPercent = 115;
                preserveParagraphs = false;
            }
            case "PORTFOLIO_DESCRIPTION_IMPROVEMENT" -> {
                minPercent = 85;
                maxPercent = 115;
                preserveParagraphs = true;
            }
            default -> throw new IllegalArgumentException("Unsupported correction task type: " + taskType);
        }
        int originalLength = original.length();
        int maxChars = Math.max(650, ceilPercent(originalLength, maxPercent));
        int minChars = Math.min(maxChars, Math.max(1, ceilPercent(originalLength, minPercent)));
        int targetChars = Math.min(maxChars, Math.max(minChars, originalLength));
        return Map.of(
                "tone", "professional",
                "min_chars", minChars,
                "target_chars", targetChars,
                "max_chars", maxChars,
                "preserve_paragraphs", preserveParagraphs,
                "preserve_facts_only", true);
    }

    private static int ceilPercent(int value, int percent) {
        return (value * percent + 99) / 100;
    }

    public static String taskType(String correctionType) {
        return switch (correctionType == null ? "" : correctionType) {
            case "SELF_INTRO" -> "SELF_INTRO_CORRECTION";
            case "INTERVIEW_ANSWER" -> "INTERVIEW_ANSWER_CORRECTION";
            case "RESUME" -> "RESUME_EXPRESSION_IMPROVEMENT";
            case "PORTFOLIO" -> "PORTFOLIO_DESCRIPTION_IMPROVEMENT";
            default -> throw new IllegalArgumentException("Unsupported correction type: " + correctionType);
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
