package com.careertuner.profile.ai;

import com.careertuner.profile.domain.UserProfile;

public interface ProfileAiService {

    ProfileAiResult evaluate(UserProfile profile, String featureType);
}
