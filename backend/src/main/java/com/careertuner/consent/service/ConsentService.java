package com.careertuner.consent.service;

import java.util.List;

import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.dto.ConsentRequest;
import com.careertuner.consent.dto.ConsentStatusResponse;
import com.careertuner.consent.dto.ConsentView;

public interface ConsentService {

    ConsentStatusResponse status(AuthUser authUser);

    ConsentStatusResponse save(AuthUser authUser, ConsentRequest request, String source);

    ConsentStatusResponse revokeAi(AuthUser authUser);

    boolean hasCurrentConsent(Long userId, String consentType);

    List<ConsentView> adminConsents(AuthUser authUser, String keyword, String consentType, int limit);
}
