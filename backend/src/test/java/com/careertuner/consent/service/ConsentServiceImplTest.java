package com.careertuner.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.auth.domain.UserConsent;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.domain.ConsentType;
import com.careertuner.consent.dto.ConsentRequest;
import com.careertuner.consent.dto.ConsentStatusResponse;
import com.careertuner.consent.dto.ConsentView;
import com.careertuner.consent.mapper.ConsentMapper;

class ConsentServiceImplTest {

    private static final AuthUser USER = new AuthUser(7L, "user@example.com", "USER");

    private final ConsentMapper mapper = mock(ConsentMapper.class);
    private final Map<String, ConsentView> latest = new LinkedHashMap<>();
    private final ConsentServiceImpl service = new ConsentServiceImpl(mapper);
    private long sequence;

    @BeforeEach
    void setUp() {
        latest.clear();
        sequence = 0L;
        when(mapper.findLatest(anyLong(), anyString()))
                .thenAnswer(invocation -> latest.get(invocation.getArgument(1, String.class)));
        when(mapper.findByUserId(anyLong())).thenAnswer(invocation -> new ArrayList<>(latest.values()));
        doAnswer(invocation -> {
            UserConsent row = invocation.getArgument(0);
            row.setId(++sequence);
            latest.put(row.getConsentType(), new ConsentView(
                    row.getId(), row.getUserId(), USER.email(), row.getConsentType(), row.getConsentVersion(), row.isAgreed(),
                    row.getAgreedAt(), row.getRevokedAt(), row.getSource(), row.getCreatedAt()));
            return null;
        }).when(mapper).insert(any(UserConsent.class));
    }

    @Test
    void savesFiveIndependentConsentTypesAndAllowsRequiredWithdrawal() {
        ConsentStatusResponse response = service.save(USER,
                new ConsentRequest(false, false, true, true, false), "SETTINGS");

        assertThat(response.termsAgreed()).isFalse();
        assertThat(response.privacyAgreed()).isFalse();
        assertThat(response.aiDataAgreed()).isTrue();
        assertThat(response.resumeAnalysisAgreed()).isTrue();
        assertThat(response.requiredConsentsMissing()).isTrue();
        assertThat(latest.values()).allMatch(row -> "v2026.07".equals(row.getConsentVersion()));
        assertThat(latest.keySet()).containsExactly(
                "TERMS", "PRIVACY", "AI_DATA", "RESUME_ANALYSIS", "MARKETING");
        verify(mapper, times(5)).insert(any(UserConsent.class));
    }

    @Test
    void unchangedSaveDoesNotDuplicateAuditRows() {
        ConsentRequest request = new ConsentRequest(true, true, true, true, false);
        service.save(USER, request, "SETTINGS");
        service.save(USER, request, "SETTINGS");

        verify(mapper, times(5)).insert(any(UserConsent.class));
    }

    @Test
    void outdatedDocumentVersionRequiresFreshConsentRecord() {
        ConsentRequest request = new ConsentRequest(true, true, true, true, false);
        service.save(USER, request, "REGISTER");
        latest.get("AI_DATA").setConsentVersion("v2026.06");

        assertThat(service.hasCurrentConsent(USER.id(), ConsentType.AI_DATA)).isFalse();

        service.save(USER, request, "SETTINGS");

        assertThat(service.hasCurrentConsent(USER.id(), ConsentType.AI_DATA)).isTrue();
        assertThat(latest.get("AI_DATA").getConsentVersion()).isEqualTo("v2026.07");
        verify(mapper, times(6)).insert(any(UserConsent.class));
    }

    @Test
    void genericRevokeRecordsHistoryAndCanBeReagreed() {
        service.save(USER, new ConsentRequest(true, true, true, true, false), "REGISTER");

        ConsentStatusResponse revoked = service.revoke(USER, ConsentType.RESUME_ANALYSIS);
        assertThat(revoked.resumeAnalysisAgreed()).isFalse();
        assertThat(latest.get("RESUME_ANALYSIS").getRevokedAt()).isNotNull();

        ConsentStatusResponse reagreed = service.save(
                USER, new ConsentRequest(true, true, true, true, false), "SETTINGS");
        assertThat(reagreed.resumeAnalysisAgreed()).isTrue();
        assertThat(latest.get("RESUME_ANALYSIS").getAgreedAt()).isNotNull();
    }
}
