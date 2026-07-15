package com.careertuner.support.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.mapper.ProfileMapper;
import com.careertuner.profile.service.ProfileService;

import tools.jackson.databind.ObjectMapper;

class OnboardingProfileSaveTest {

    private static final AuthUser USER = new AuthUser(7L, "user@example.com", "USER");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void conflictReloadsLatestProfileAndMergesCollectedFieldsOnce() {
        ProfileMapper mapper = mock(ProfileMapper.class);
        ProfileService service = mock(ProfileService.class);
        UserProfile stale = UserProfile.builder()
                .userId(7L)
                .versionNo(3)
                .skills("[\"Java\"]")
                .selfIntro("이전 자기소개")
                .build();
        UserProfile latest = UserProfile.builder()
                .userId(7L)
                .versionNo(4)
                .skills("[\"Java\",\"Docker\"]")
                .selfIntro("다른 화면에서 저장한 자기소개")
                .build();
        when(mapper.findByUserId(7L)).thenReturn(stale, latest);
        when(service.save(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "stale"))
                .thenReturn(null);

        OnboardingProfileSave.saveWithSingleConflictRetry(
                mapper, service, USER, "백엔드 개발자", List.of("Spring", "Docker"), objectMapper);

        ArgumentCaptor<UserProfileRequest> requests = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(service, times(2)).save(any(), requests.capture());
        assertThat(requests.getAllValues().get(0).baseVersionNo()).isEqualTo(3);
        UserProfileRequest retried = requests.getAllValues().get(1);
        assertThat(retried.baseVersionNo()).isEqualTo(4);
        assertThat(retried.selfIntro()).isEqualTo("다른 화면에서 저장한 자기소개");
        assertThat(retried.skills()).isEqualTo(List.of("Java", "Docker", "Spring"));
        verify(mapper, times(2)).findByUserId(7L);
    }

    @Test
    void sameDesiredJobChangedRemotelyStopsWithOriginalConflict() {
        ProfileMapper mapper = mock(ProfileMapper.class);
        ProfileService service = mock(ProfileService.class);
        UserProfile base = UserProfile.builder()
                .userId(7L)
                .versionNo(3)
                .desiredJob("백엔드 개발자")
                .skills("[\"Java\"]")
                .build();
        UserProfile latest = UserProfile.builder()
                .userId(7L)
                .versionNo(4)
                .desiredJob("데이터 엔지니어")
                .skills("[\"Java\",\"SQL\"]")
                .build();
        BusinessException conflict = new BusinessException(ErrorCode.CONFLICT, "stale");
        when(mapper.findByUserId(7L)).thenReturn(base, latest);
        when(service.save(any(), any())).thenThrow(conflict);

        assertThatThrownBy(() -> OnboardingProfileSave.saveWithSingleConflictRetry(
                mapper, service, USER, "플랫폼 엔지니어", List.of("Spring"), objectMapper))
                .isSameAs(conflict);

        verify(service, times(1)).save(any(), any());
        verify(mapper, times(2)).findByUserId(7L);
    }

    @Test
    void unchangedDesiredJobPreservesRemoteEditAndStillMergesOtherFields() {
        ProfileMapper mapper = mock(ProfileMapper.class);
        ProfileService service = mock(ProfileService.class);
        UserProfile base = UserProfile.builder()
                .userId(7L)
                .versionNo(3)
                .desiredJob("백엔드 개발자")
                .skills("[\"Java\"]")
                .selfIntro("기존 자기소개")
                .build();
        UserProfile latest = UserProfile.builder()
                .userId(7L)
                .versionNo(4)
                .desiredJob("데이터 엔지니어")
                .skills("[\"Java\",\"SQL\"]")
                .selfIntro("다른 화면에서 저장한 자기소개")
                .build();
        when(mapper.findByUserId(7L)).thenReturn(base, latest);
        when(service.save(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "stale"))
                .thenReturn(null);

        OnboardingProfileSave.saveWithSingleConflictRetry(
                mapper, service, USER, "백엔드 개발자", List.of("Spring"), objectMapper);

        ArgumentCaptor<UserProfileRequest> requests = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(service, times(2)).save(any(), requests.capture());
        UserProfileRequest retried = requests.getAllValues().get(1);
        assertThat(retried.desiredJob()).isEqualTo("데이터 엔지니어");
        assertThat(retried.selfIntro()).isEqualTo("다른 화면에서 저장한 자기소개");
        assertThat(retried.skills()).isEqualTo(List.of("Java", "SQL", "Spring"));
        assertThat(retried.baseVersionNo()).isEqualTo(4);
    }

    @Test
    void matchingConcurrentDesiredJobCanRetrySafely() {
        ProfileMapper mapper = mock(ProfileMapper.class);
        ProfileService service = mock(ProfileService.class);
        UserProfile base = UserProfile.builder()
                .userId(7L)
                .versionNo(3)
                .desiredJob("백엔드 개발자")
                .build();
        UserProfile latest = UserProfile.builder()
                .userId(7L)
                .versionNo(4)
                .desiredJob("플랫폼 엔지니어")
                .build();
        when(mapper.findByUserId(7L)).thenReturn(base, latest);
        when(service.save(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "stale"))
                .thenReturn(null);

        OnboardingProfileSave.saveWithSingleConflictRetry(
                mapper, service, USER, "플랫폼 엔지니어", List.of("Spring"), objectMapper);

        ArgumentCaptor<UserProfileRequest> requests = ArgumentCaptor.forClass(UserProfileRequest.class);
        verify(service, times(2)).save(any(), requests.capture());
        assertThat(requests.getAllValues().get(1).desiredJob()).isEqualTo("플랫폼 엔지니어");
        assertThat(requests.getAllValues().get(1).baseVersionNo()).isEqualTo(4);
    }

    @Test
    void nonConflictFailureIsNotRetried() {
        ProfileMapper mapper = mock(ProfileMapper.class);
        ProfileService service = mock(ProfileService.class);
        when(mapper.findByUserId(7L)).thenReturn(UserProfile.builder().userId(7L).versionNo(1).build());
        when(service.save(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "invalid"));

        assertThatThrownBy(() -> OnboardingProfileSave.saveWithSingleConflictRetry(
                mapper, service, USER, null, List.of("Java"), objectMapper))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(service, times(1)).save(any(), any());
        verify(mapper, times(1)).findByUserId(7L);
    }

    @Test
    void secondConflictStopsAfterOneRetry() {
        ProfileMapper mapper = mock(ProfileMapper.class);
        ProfileService service = mock(ProfileService.class);
        when(mapper.findByUserId(7L)).thenReturn(
                UserProfile.builder().userId(7L).versionNo(1).build(),
                UserProfile.builder().userId(7L).versionNo(2).build());
        when(service.save(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "stale"));

        assertThatThrownBy(() -> OnboardingProfileSave.saveWithSingleConflictRetry(
                mapper, service, USER, "개발자", List.of("Java"), objectMapper))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(service, times(2)).save(any(), any());
        verify(mapper, times(2)).findByUserId(7L);
    }
}
