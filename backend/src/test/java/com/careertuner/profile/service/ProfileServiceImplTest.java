package com.careertuner.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.service.ConsentService;
import com.careertuner.profile.ai.JobFamily;
import com.careertuner.profile.ai.ProfileAiResult;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.ai.ProfileCriterionScore;
import com.careertuner.profile.ai.ScoreCriterion;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.mapper.ProfileMapper;

import tools.jackson.databind.ObjectMapper;

class ProfileServiceImplTest {

    private static final AuthUser USER = new AuthUser(7L, "user@example.com", "USER");

    @Test
    void blocksAiWhenAiDataConsentIsMissing() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ApplicationCaseMapper usageMapper = mock(ApplicationCaseMapper.class);
        ConsentService consentService = mock(ConsentService.class);
        ProfileAiService aiService = mock(ProfileAiService.class);
        when(consentService.hasCurrentConsent(7L, "AI_DATA")).thenReturn(false);
        ProfileServiceImpl service = new ProfileServiceImpl(
                profileMapper,
                usageMapper,
                consentService,
                aiService,
                mock(NotificationService.class),
                new ObjectMapper());

        assertThatThrownBy(() -> service.summarize(USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 데이터 사용 동의");
        verify(aiService, never()).evaluate(any(), any());
        verify(usageMapper, never()).insertAiUsageLog(any());
    }

    @Test
    void recordsModelTokensAndStatusAfterAiRun() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ApplicationCaseMapper usageMapper = mock(ApplicationCaseMapper.class);
        ConsentService consentService = mock(ConsentService.class);
        ProfileAiService aiService = mock(ProfileAiService.class);
        UserProfile profile = UserProfile.builder()
                .userId(7L)
                .desiredJob("마케팅 AE")
                .build();
        when(consentService.hasCurrentConsent(7L, "AI_DATA")).thenReturn(true);
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(aiService.evaluate(eq(profile), eq("PROFILE_SUMMARY"))).thenReturn(result("FALLBACK"));
        ProfileServiceImpl service = new ProfileServiceImpl(
                profileMapper,
                usageMapper,
                consentService,
                aiService,
                mock(NotificationService.class),
                new ObjectMapper());

        ProfileAiResponse response = service.summarize(USER);

        assertThat(response.status()).isEqualTo("FALLBACK");
        assertThat(response.model()).isEqualTo("profile-test-model");
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageMapper).insertAiUsageLog(captor.capture());
        AiUsageLog log = captor.getValue();
        assertThat(log.getUserId()).isEqualTo(7L);
        assertThat(log.getFeatureType()).isEqualTo("PROFILE_SUMMARY");
        assertThat(log.getStatus()).isEqualTo("FALLBACK");
        assertThat(log.getModel()).isEqualTo("profile-test-model");
        assertThat(log.getInputTokens()).isEqualTo(11);
        assertThat(log.getOutputTokens()).isEqualTo(13);
        assertThat(log.getTokenUsage()).isEqualTo(24);
    }

    private ProfileAiResult result(String status) {
        return new ProfileAiResult(
                "PROFILE_SUMMARY",
                "요약",
                List.of("마케팅"),
                List.of("강점"),
                List.of("보완점"),
                List.of("추천"),
                77,
                JobFamily.SALES_MARKETING,
                List.of(new ProfileCriterionScore(
                        ScoreCriterion.JOB_SKILL_ALIGNMENT,
                        80,
                        20,
                        16.0,
                        "근거",
                        "개선")),
                new CareerAnalysisAiUsage("profile-test-model", 11, 13, 24, false),
                status,
                status.equals("FALLBACK") ? "upstream failure" : null);
    }
}
