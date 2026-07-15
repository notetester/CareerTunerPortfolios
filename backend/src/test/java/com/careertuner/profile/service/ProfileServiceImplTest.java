package com.careertuner.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.analysis.ai.provider.CareerAnalysisAiUsage;
import com.careertuner.applicationcase.domain.AiUsageLog;
import com.careertuner.applicationcase.mapper.ApplicationCaseMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.consent.service.ConsentService;
import com.careertuner.profile.ai.JobFamily;
import com.careertuner.profile.ai.ProfileAiResult;
import com.careertuner.profile.ai.ProfileAiService;
import com.careertuner.profile.ai.ProfileCriterionScore;
import com.careertuner.profile.ai.ScoreCriterion;
import com.careertuner.notification.service.NotificationService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.domain.UserProfileVersion;
import com.careertuner.profile.domain.ProfileAiAnalysis;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.mapper.ProfileAiAnalysisMapper;
import com.careertuner.profile.mapper.ProfileMapper;

import tools.jackson.databind.ObjectMapper;

class ProfileServiceImplTest {

    private static final AuthUser USER = new AuthUser(7L, "user@example.com", "USER");

    @Test
    void saveCreatesImmutableSnapshotAfterCurrentProfileUpsert() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        UserProfile current = UserProfile.builder().id(11L).userId(7L).desiredJob("서버 개발자").versionNo(1).build();
        UserProfile saved = UserProfile.builder().id(11L).userId(7L).desiredJob("백엔드 개발자").versionNo(2).build();
        when(profileMapper.insertEmptyIfAbsent(7L)).thenReturn(0);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(current);
        when(profileMapper.findByUserId(7L)).thenReturn(saved);
        when(profileMapper.findVersions(7L, 20)).thenReturn(List.of());
        ProfileServiceImpl service = newService(
                profileMapper,
                mock(ApplicationCaseMapper.class),
                mock(ConsentService.class),
                mock(ProfileAiService.class));

        service.save(USER, request("백엔드 개발자", 1));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(profileMapper);
        order.verify(profileMapper).insertEmptyIfAbsent(7L);
        order.verify(profileMapper).findByUserIdForUpdate(7L);
        order.verify(profileMapper).upsert(any(UserProfile.class));
        order.verify(profileMapper).insertVersionFromCurrent(7L, "MANUAL_SAVE");
        assertThat(service.versions(USER, 20)).isEmpty();
    }

    @Test
    void firstSaveInitializesTheLockedEmptyRowWithoutSkippingVersionOne() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        UserProfile empty = UserProfile.builder().id(11L).userId(7L).versionNo(1).build();
        UserProfile saved = UserProfile.builder().id(11L).userId(7L).desiredJob("백엔드 개발자").versionNo(1).build();
        when(profileMapper.insertEmptyIfAbsent(7L)).thenReturn(1);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(empty);
        when(profileMapper.findByUserId(7L)).thenReturn(saved);
        ProfileServiceImpl service = newService(
                profileMapper,
                mock(ApplicationCaseMapper.class),
                mock(ConsentService.class),
                mock(ProfileAiService.class));

        var response = service.save(USER, request("백엔드 개발자", null));

        assertThat(response.versionNo()).isEqualTo(1);
        verify(profileMapper).initialize(any(UserProfile.class));
        verify(profileMapper, never()).upsert(any(UserProfile.class));
        verify(profileMapper).insertVersionFromCurrent(7L, "MANUAL_SAVE");
    }

    @Test
    void emptyRowCreatedByAnotherProfileFeatureStillAllowsTheFirstNullBaseSave() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        UserProfile empty = UserProfile.builder().id(11L).userId(7L).versionNo(1).build();
        UserProfile saved = UserProfile.builder().id(11L).userId(7L).desiredJob("백엔드 개발자").versionNo(2).build();
        when(profileMapper.insertEmptyIfAbsent(7L)).thenReturn(0);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(empty);
        when(profileMapper.findByUserId(7L)).thenReturn(saved);
        ProfileServiceImpl service = newService(
                profileMapper,
                mock(ApplicationCaseMapper.class),
                mock(ConsentService.class),
                mock(ProfileAiService.class));

        var response = service.save(USER, request("백엔드 개발자", null));

        assertThat(response.versionNo()).isEqualTo(2);
        verify(profileMapper).upsert(any(UserProfile.class));
        verify(profileMapper, never()).initialize(any(UserProfile.class));
    }

    @Test
    void missingBaseVersionIsRejectedForAnExistingProfile() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        UserProfile current = UserProfile.builder()
                .id(11L)
                .userId(7L)
                .desiredJob("백엔드 개발자")
                .versionNo(3)
                .build();
        when(profileMapper.insertEmptyIfAbsent(7L)).thenReturn(0);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(current);
        ProfileServiceImpl service = newService(
                profileMapper,
                mock(ApplicationCaseMapper.class),
                mock(ConsentService.class),
                mock(ProfileAiService.class));

        assertThatThrownBy(() -> service.save(USER, request("프론트엔드 개발자", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(profileMapper, never()).upsert(any(UserProfile.class));
        verify(profileMapper, never()).initialize(any(UserProfile.class));
        verify(profileMapper, never()).insertVersionFromCurrent(7L, "MANUAL_SAVE");
    }

    @Test
    void missingBaseVersionIsRejectedAfterAnExistingProfileWasCleared() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        UserProfile clearedExisting = UserProfile.builder()
                .id(11L)
                .userId(7L)
                .versionNo(2)
                .build();
        when(profileMapper.insertEmptyIfAbsent(7L)).thenReturn(0);
        when(profileMapper.findByUserIdForUpdate(7L)).thenReturn(clearedExisting);
        ProfileServiceImpl service = newService(
                profileMapper,
                mock(ApplicationCaseMapper.class),
                mock(ConsentService.class),
                mock(ProfileAiService.class));

        assertThatThrownBy(() -> service.save(USER, request("뒤늦은 저장", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(profileMapper, never()).upsert(any(UserProfile.class));
        verify(profileMapper, never()).insertVersionFromCurrent(7L, "MANUAL_SAVE");
    }

    @Test
    void secondWriterUsingTheSameReadVersionGetsConflictInsteadOfOverwritingTheFirstWriter() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        AtomicReference<UserProfile> database = new AtomicReference<>(UserProfile.builder()
                .id(11L)
                .userId(7L)
                .desiredJob("기존 직무")
                .versionNo(3)
                .build());
        when(profileMapper.insertEmptyIfAbsent(7L)).thenReturn(0);
        when(profileMapper.findByUserIdForUpdate(7L)).thenAnswer(invocation -> database.get());
        when(profileMapper.findByUserId(7L)).thenAnswer(invocation -> database.get());
        doAnswer(invocation -> {
            UserProfile incoming = invocation.getArgument(0);
            UserProfile before = database.get();
            database.set(UserProfile.builder()
                    .id(before.getId())
                    .userId(before.getUserId())
                    .desiredJob(incoming.getDesiredJob())
                    .versionNo(before.getVersionNo() + 1)
                    .build());
            return null;
        }).when(profileMapper).upsert(any(UserProfile.class));
        ProfileServiceImpl service = newService(
                profileMapper,
                mock(ApplicationCaseMapper.class),
                mock(ConsentService.class),
                mock(ProfileAiService.class));

        var firstWriter = service.save(USER, request("첫 번째 저장", 3));
        assertThat(firstWriter.versionNo()).isEqualTo(4);
        assertThatThrownBy(() -> service.save(USER, request("뒤늦은 두 번째 저장", 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최신 내용을 다시 불러온 뒤")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        assertThat(database.get().getDesiredJob()).isEqualTo("첫 번째 저장");
        assertThat(database.get().getVersionNo()).isEqualTo(4);
        verify(profileMapper, times(1)).upsert(any(UserProfile.class));
        verify(profileMapper, times(1)).insertVersionFromCurrent(7L, "MANUAL_SAVE");
    }

    @Test
    void blocksAiWhenAiDataConsentIsMissing() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ApplicationCaseMapper usageMapper = mock(ApplicationCaseMapper.class);
        ConsentService consentService = mock(ConsentService.class);
        ProfileAiService aiService = mock(ProfileAiService.class);
        when(consentService.hasCurrentConsent(7L, "AI_DATA")).thenReturn(false);
        ProfileServiceImpl service = newService(
                profileMapper, usageMapper, consentService, aiService);

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
                .versionNo(3)
                .build();
        UserProfileVersion analyzedVersion = UserProfileVersion.builder()
                .id(401L)
                .userId(7L)
                .versionNo(3)
                .build();
        when(consentService.hasCurrentConsent(7L, "AI_DATA")).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, "RESUME_ANALYSIS")).thenReturn(true);
        when(profileMapper.findByUserId(7L)).thenReturn(profile);
        when(profileMapper.findVersionByNo(7L, 3)).thenReturn(analyzedVersion);
        when(aiService.evaluate(eq(profile), eq("PROFILE_SUMMARY"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("FALLBACK"));
        ProfileServiceImpl service = newService(
                profileMapper, usageMapper, consentService, aiService);

        ProfileAiResponse response = service.summarize(USER);

        assertThat(response.status()).isEqualTo("FALLBACK");
        assertThat(response.model()).isEqualTo("profile-test-model");
        assertThat(response.profileVersionId()).isEqualTo(401L);
        assertThat(response.profileVersionNo()).isEqualTo(3);
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

    @Test
    void immediateResponsesAndPersistedAnalysisKeepTheEvaluatedVersionWhenLatestChanges() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileAiAnalysisMapper analysisMapper = mock(ProfileAiAnalysisMapper.class);
        ApplicationCaseMapper usageMapper = mock(ApplicationCaseMapper.class);
        ConsentService consentService = mock(ConsentService.class);
        ProfileAiService aiService = mock(ProfileAiService.class);
        UserProfile evaluatedProfile = UserProfile.builder()
                .userId(7L)
                .desiredJob("프론트엔드 개발자")
                .versionNo(3)
                .build();
        UserProfileVersion evaluatedVersion = UserProfileVersion.builder()
                .id(401L)
                .userId(7L)
                .versionNo(3)
                .build();
        UserProfileVersion laterVersion = UserProfileVersion.builder()
                .id(999L)
                .userId(7L)
                .versionNo(4)
                .build();
        when(consentService.hasCurrentConsent(7L, "AI_DATA")).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, "RESUME_ANALYSIS")).thenReturn(true);
        when(profileMapper.findByUserId(7L)).thenReturn(evaluatedProfile);
        when(profileMapper.findVersionByNo(7L, 3)).thenReturn(evaluatedVersion);
        when(profileMapper.findLatestVersion(7L)).thenReturn(laterVersion);
        when(aiService.evaluate(eq(evaluatedProfile), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> result(invocation.getArgument(1), "SUCCESS"));
        ProfileServiceImpl service = newService(
                profileMapper, analysisMapper, usageMapper, consentService, aiService);

        ProfileAiResponse summary = service.summarize(USER);
        ProfileCompletenessResponse completeness = service.diagnoseCompleteness(USER);

        assertThat(summary.profileVersionId()).isEqualTo(401L);
        assertThat(summary.profileVersionNo()).isEqualTo(3);
        assertThat(completeness.profileVersionId()).isEqualTo(401L);
        assertThat(completeness.profileVersionNo()).isEqualTo(3);
        ArgumentCaptor<ProfileAiAnalysis> persisted = ArgumentCaptor.forClass(ProfileAiAnalysis.class);
        verify(analysisMapper, org.mockito.Mockito.times(2)).upsert(persisted.capture());
        assertThat(persisted.getAllValues())
                .extracting(ProfileAiAnalysis::getProfileVersionId)
                .containsOnly(401L);
        verify(profileMapper, never()).findLatestVersion(7L);
    }

    @Test
    void blocksProfileAiWhenResumeAnalysisConsentIsMissing() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ApplicationCaseMapper usageMapper = mock(ApplicationCaseMapper.class);
        ConsentService consentService = mock(ConsentService.class);
        ProfileAiService aiService = mock(ProfileAiService.class);
        when(consentService.hasCurrentConsent(7L, "AI_DATA")).thenReturn(true);
        when(consentService.hasCurrentConsent(7L, "RESUME_ANALYSIS")).thenReturn(false);
        ProfileServiceImpl service = newService(profileMapper, usageMapper, consentService, aiService);

        assertThatThrownBy(() -> service.summarize(USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이력서 분석 개인정보");
        verify(aiService, never()).evaluate(any(), any());
    }

    private ProfileAiResult result(String status) {
        return result("PROFILE_SUMMARY", status);
    }

    private static UserProfileRequest request(String desiredJob, Integer baseVersionNo) {
        return new UserProfileRequest(
                desiredJob, "IT", null, null, null, null,
                null, null, null, null, null, null, baseVersionNo);
    }

    private ProfileAiResult result(String featureType, String status) {
        return new ProfileAiResult(
                featureType,
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

    private static ProfileServiceImpl newService(
            ProfileMapper profileMapper,
            ApplicationCaseMapper usageMapper,
            ConsentService consentService,
            ProfileAiService aiService) {
        return newService(profileMapper, mock(com.careertuner.profile.mapper.ProfileAiAnalysisMapper.class),
                usageMapper, consentService, aiService);
    }

    private static ProfileServiceImpl newService(
            ProfileMapper profileMapper,
            ProfileAiAnalysisMapper profileAiAnalysisMapper,
            ApplicationCaseMapper usageMapper,
            ConsentService consentService,
            ProfileAiService aiService) {
        return new ProfileServiceImpl(
                profileMapper,
                profileAiAnalysisMapper,
                usageMapper,
                consentService,
                aiService,
                mock(NotificationService.class),
                new ObjectMapper(),
                mock(com.careertuner.file.service.FileService.class),
                mock(ProfilePortfolioService.class),
                new com.careertuner.common.text.DocumentTextExtractor(),
                mock(com.careertuner.profile.ai.ProfileResumeStructurer.class),
                mock(org.springframework.transaction.support.TransactionTemplate.class));
    }
}
