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
        Map<String, Object> constraints
) {

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
        return new SelfCorrectionInput(
                "runtime-" + UUID.randomUUID(),
                taskType(command.correctionType()),
                original,
                command.applicationCase() == null ? "" : safe(command.applicationCase().getJobTitle()),
                Map.of(),
                List.of(),
                Map.of(
                        "tone", "professional",
                        "max_chars", Math.min(4000, Math.max(650, original.length() + 300)),
                        "preserve_facts_only", true));
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
