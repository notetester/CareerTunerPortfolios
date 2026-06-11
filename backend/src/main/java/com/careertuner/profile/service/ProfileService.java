package com.careertuner.profile.service;

import java.util.List;

import com.careertuner.common.security.AuthUser;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;

public interface ProfileService {

    UserProfileResponse me(AuthUser authUser);

    UserProfileResponse save(AuthUser authUser, UserProfileRequest request);

    ProfileAiResponse summarize(AuthUser authUser);

    ProfileAiResponse extractSkills(AuthUser authUser);

    ProfileCompletenessResponse diagnoseCompleteness(AuthUser authUser);

    List<UserProfileResponse> adminProfiles(AuthUser authUser, String keyword, int limit);

    UserProfileResponse adminProfile(AuthUser authUser, Long userId);
}
