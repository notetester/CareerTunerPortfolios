package com.careertuner.legal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.legal.dto.LegalDocResponse;
import com.careertuner.legal.mapper.LegalMapper;

class LegalServiceImplTest {

    @Test
    void returnsResumeConsentFallbackWhenAdminHasNotPublishedOneYet() {
        LegalMapper mapper = mock(LegalMapper.class);
        when(mapper.findLiveVersion("RESUME_CONSENT")).thenReturn(null);
        LegalServiceImpl service = new LegalServiceImpl(mapper);

        LegalDocResponse response = service.getPublicDoc("resume-analysis-consent");

        assertThat(response.title()).contains("이력서 분석");
        assertThat(response.versionLabel()).isEqualTo("v2026.07");
        assertThat(response.sections()).hasSizeGreaterThanOrEqualTo(4);
    }
}
