package com.careertuner.consent.service;

import java.util.List;

import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.dto.ConsentRequest;
import com.careertuner.consent.dto.ConsentStatusResponse;
import com.careertuner.consent.dto.ConsentView;

public interface ConsentService {

    ConsentStatusResponse status(AuthUser authUser);

    ConsentStatusResponse save(AuthUser authUser, ConsentRequest request, String source);

    ConsentStatusResponse revokeAi(AuthUser authUser);

    ConsentStatusResponse revoke(AuthUser authUser, ConsentType consentType);

    boolean hasCurrentConsent(Long userId, String consentType);

    default boolean hasCurrentConsent(Long userId, ConsentType consentType) {
        return hasCurrentConsent(userId, consentType.name());
    }

    default boolean hasRequiredConsents(Long userId) {
        return hasCurrentConsent(userId, ConsentType.TERMS)
                && hasCurrentConsent(userId, ConsentType.PRIVACY);
    }

    List<ConsentView> adminConsents(AuthUser authUser, String keyword, String consentType,
                                    String status, String source, String from, String to, int limit);
}
