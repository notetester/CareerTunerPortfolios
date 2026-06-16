package com.careertuner.correction.dto;

import java.util.List;

public record CorrectionResultPayload(
        String summary,
        List<String> issues,
        List<String> changeReasons,
        List<String> suggestions
) {

    public static CorrectionResultPayload empty() {
        return new CorrectionResultPayload("", List.of(), List.of(), List.of());
    }
}
