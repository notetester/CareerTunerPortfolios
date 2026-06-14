package com.careertuner.consent.dto;

public record ConsentRequest(
        boolean termsAgreed,
        boolean privacyAgreed,
        boolean aiDataAgreed,
        boolean marketingAgreed
) {
}
