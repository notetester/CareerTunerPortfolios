package com.careertuner.admin.guideline.service;

import java.util.List;

import com.careertuner.admin.guideline.dto.AdminGuidelineRequest;
import com.careertuner.admin.guideline.dto.AdminGuidelineResponse;
import com.careertuner.common.security.AuthUser;

public interface AdminGuidelineService {

    List<AdminGuidelineResponse> getGuidelines(AuthUser authUser);

    AdminGuidelineResponse getGuideline(AuthUser authUser, Long id);

    AdminGuidelineResponse getPublished(AuthUser authUser);

    AdminGuidelineResponse createGuideline(AuthUser authUser, AdminGuidelineRequest request);

    AdminGuidelineResponse updateGuideline(AuthUser authUser, Long id, AdminGuidelineRequest request);

    AdminGuidelineResponse publishGuideline(AuthUser authUser, Long id);

    void deleteGuideline(AuthUser authUser, Long id);
}
