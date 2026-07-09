package com.careertuner.profile.service;

import java.util.List;

import com.careertuner.common.security.AuthUser;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileAnalyzeResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.ProfileDocumentAnalyzeRequest;
import com.careertuner.profile.dto.ProfileDocumentImportRequest;
import com.careertuner.profile.dto.ProfileImportResponse;
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

    /**
     * 업로드된 파일 텍스트를 resume_text 또는 self_intro 에 덤프.
     * download+extract 는 트랜잭션 밖, RMW 저장만 트랜잭션.
     */
    ProfileImportResponse importDocument(AuthUser authUser, ProfileDocumentImportRequest request);

    /** 이력서 구조화 분석 비동기 발사. 즉시 jobId(PENDING) 반환. */
    ProfileAnalyzeResponse startAnalyze(AuthUser authUser, ProfileDocumentAnalyzeRequest request);

    /** 구조화 분석 작업 조회. */
    ProfileAnalyzeResponse getAnalyze(AuthUser authUser, String jobId);
}
