package com.careertuner.correction.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.correction.domain.CorrectionRequest;
import com.careertuner.billing.dto.AiChargeResult;

public record CorrectionResponse(
        Long id,
        Long applicationCaseId,
        String correctionType,
        String sourceType,
        Long sourceRefId,
        String originalText,
        String improvedText,
        String sourceSnapshot,
        String summary,
        List<String> issues,
        List<String> changeReasons,
        List<String> suggestions,
        String status,
        Long aiUsageLogId,
        String chargeType,
        int chargedCredit,
        int totalTokens,
        int remainingCredit,
        boolean replayed,
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
                correction.getSourceSnapshot(),
                safePayload.summary(),
                safePayload.issues(),
                safePayload.changeReasons(),
                safePayload.suggestions(),
                correction.getStatus(),
                correction.getAiUsageLogId(),
                null,
                0,
                0,
                0,
                false,
                correction.getCreatedAt());
    }

    public static CorrectionResponse from(CorrectionRequest correction,
                                          CorrectionResultPayload payload,
                                          AiChargeResult chargeResult,
                                          int totalTokens) {
        CorrectionResponse base = from(correction, payload);
        return new CorrectionResponse(
                base.id(), base.applicationCaseId(), base.correctionType(), base.sourceType(), base.sourceRefId(),
                base.originalText(), base.improvedText(), base.sourceSnapshot(), base.summary(), base.issues(), base.changeReasons(),
                base.suggestions(), base.status(), base.aiUsageLogId(),
                chargeResult == null ? null : chargeResult.chargeType().name(),
                chargeResult == null ? 0 : chargeResult.chargedCredit(),
                Math.max(0, totalTokens),
                chargeResult == null ? 0 : chargeResult.remainingCredit(),
                false,
                base.createdAt());
    }

    public CorrectionResponse asReplayed() {
        return new CorrectionResponse(
                id, applicationCaseId, correctionType, sourceType, sourceRefId,
                originalText, improvedText, sourceSnapshot, summary, issues, changeReasons, suggestions,
                status, aiUsageLogId, chargeType, chargedCredit, totalTokens, remainingCredit,
                true, createdAt);
    }
}
