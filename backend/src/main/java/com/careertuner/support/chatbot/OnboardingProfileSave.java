package com.careertuner.support.chatbot;

import java.util.List;
import java.util.Objects;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.mapper.ProfileMapper;
import com.careertuner.profile.service.ProfileService;

import tools.jackson.databind.ObjectMapper;

/** 온보딩 슬롯과 최신 프로필을 병합해 저장하되, 안전한 optimistic-lock 충돌만 한 번 재병합한다. */
final class OnboardingProfileSave {

    private OnboardingProfileSave() {
    }

    static void saveWithSingleConflictRetry(
            ProfileMapper profileMapper,
            ProfileService profileService,
            AuthUser authUser,
            String desiredJob,
            List<String> skills,
            ObjectMapper objectMapper) {
        UserProfile base = profileMapper.findByUserId(authUser.id());
        UserProfileRequest initialRequest = OnboardingProfileMerge.merge(
                base, desiredJob, skills, objectMapper);
        try {
            profileService.save(authUser, initialRequest);
            return;
        } catch (BusinessException firstConflict) {
            if (firstConflict.getErrorCode() != ErrorCode.CONFLICT) {
                throw firstConflict;
            }

            UserProfile latest = profileMapper.findByUserId(authUser.id());
            DesiredJobRetryResolution resolution = resolveDesiredJobForRetry(base, latest, desiredJob);
            if (resolution.conflicted()) {
                // 같은 필드를 양쪽에서 다르게 수정했다. 최신값을 자동으로 덮지 말고 기존 409를 유지해
                // 사용자가 새 프로필을 확인한 뒤 명시적으로 다시 시도하게 한다.
                throw firstConflict;
            }

            UserProfileRequest retryRequest = OnboardingProfileMerge.merge(
                    latest, resolution.desiredJob(), skills, objectMapper);
            profileService.save(authUser, retryRequest);
        }
    }

    /**
     * desiredJob 3-way 정책(base / 온보딩 입력 / latest).
     * 온보딩 입력이 base와 같으면 사용자가 이 저장에서 바꾼 값이 아니므로 latest를 보존한다.
     * 양쪽이 모두 base에서 벗어났고 값이 다르면 충돌이며 자동 재시도하지 않는다.
     */
    static DesiredJobRetryResolution resolveDesiredJobForRetry(
            UserProfile base,
            UserProfile latest,
            String desiredJob) {
        String baseJob = normalizedJob(base != null ? base.getDesiredJob() : null);
        String latestJob = normalizedJob(latest != null ? latest.getDesiredJob() : null);
        String requestedJob = normalizedJob(desiredJob);

        boolean localChanged = requestedJob != null && !Objects.equals(requestedJob, baseJob);
        boolean remoteChanged = !Objects.equals(latestJob, baseJob);
        boolean conflicted = localChanged && remoteChanged && !Objects.equals(requestedJob, latestJob);
        if (conflicted) {
            return new DesiredJobRetryResolution(null, true);
        }
        // null을 넘기면 OnboardingProfileMerge가 latest의 desiredJob을 보존한다.
        return new DesiredJobRetryResolution(localChanged ? requestedJob : null, false);
    }

    private static String normalizedJob(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record DesiredJobRetryResolution(String desiredJob, boolean conflicted) {
    }
}
