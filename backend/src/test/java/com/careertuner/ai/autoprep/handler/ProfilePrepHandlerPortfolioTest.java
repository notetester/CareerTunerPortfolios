package com.careertuner.ai.autoprep.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepSlots;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.profile.ai.JobFamily;
import com.careertuner.profile.ai.ProfileAiResult;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.mapper.ProfileMapper;
import com.careertuner.profile.service.ProfilePortfolioService;

class ProfilePrepHandlerPortfolioTest {

    @Test
    void sendsLinkedPortfolioEvidenceAsItsOwnProfileField() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileAiService aiService = mock(ProfileAiService.class);
        ProfilePortfolioService portfolioService = mock(ProfilePortfolioService.class);
        UserProfile profile = UserProfile.builder().id(31L).userId(7L).build();
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(portfolioService.evidenceText(7L)).thenReturn("[포트폴리오 파일] project.md\nAPI 성능 40% 개선");
        when(aiService.evaluate(eq(profile), eq("PROFILE_SUMMARY"))).thenReturn(result());
        ProfilePrepHandler handler = new ProfilePrepHandler(profileMapper, aiService, portfolioService);

        handler.handle(
                new PrepStepContext(7L, 101L, new PrepSlots("회사", "개발자", "JOB", 101L),
                        null, List.of(), Map.of()),
                mock(PrepProgress.class));

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(aiService).evaluate(profileCaptor.capture(), eq("PROFILE_SUMMARY"));
        assertThat(profileCaptor.getValue().getPortfolioEvidence())
                .contains("[포트폴리오 파일]")
                .contains("성능 40% 개선");
        assertThat(profileCaptor.getValue().getSelfIntro()).isNull();
    }

    private static ProfileAiResult result() {
        return new ProfileAiResult(
                "PROFILE_SUMMARY", "요약", List.of(), List.of(), List.of(), List.of(), 0,
                JobFamily.GENERAL, List.of(),
                new CareerAnalysisAiUsage("test", 0, 0, 0, false), "SUCCESS", null);
    }
}
