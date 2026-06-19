package com.careertuner.correction.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.correction.domain.CorrectionRequest;

public record CorrectionResponse(
        Long id,
        Long applicationCaseId,
        String correctionType,
        String sourceType,
        Long sourceRefId,
        String originalText,
        String improvedText,
        String summary,
        List<String> issues,
        List<String> changeReasons,
        List<String> suggestions,
        String status,
        Long aiUsageLogId,
        LocalDateTime createdAt
) {

    public static CorrectionResponse from(CorrectionRequest correction,
                                          CorrectionResultPayload payload) {
        CorrectionResultPayload safePayload = payload == null ? CorrectionResultPayload.empty() : payload;
        return new CorrectionResponse(
                correction.getId(),
                correction.getApplicationCaseId(),
                correction.getCorrectionType(),
                correction.getSourceType(),
                correction.getSourceRefId(),
                correction.getOriginalText(),
                correction.getImprovedText(),
                safePayload.summary(),
                safePayload.issues(),
                safePayload.changeReasons(),
                safePayload.suggestions(),
                correction.getStatus(),
                correction.getAiUsageLogId(),
                correction.getCreatedAt());
    }
}
