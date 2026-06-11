package com.careertuner.consent.dto;

import java.util.List;

public record ConsentStatusResponse(
        boolean termsAgreed,
        boolean privacyAgreed,
        boolean aiDataAgreed,
        boolean marketingAgreed,
        boolean requiredConsentsMissing,
        List<ConsentView> history
) {
}
