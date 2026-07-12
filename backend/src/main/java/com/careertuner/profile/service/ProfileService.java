package com.careertuner.profile.service;

import java.util.List;

import com.careertuner.ai.common.model.RequestedAiModel;
import com.careertuner.common.security.AuthUser;
import com.careertuner.profile.dto.ProfileAiResponse;
import com.careertuner.profile.dto.ProfileAnalyzeResponse;
import com.careertuner.profile.dto.ProfileCompletenessResponse;
import com.careertuner.profile.dto.ProfileDocumentAnalyzeRequest;
import com.careertuner.profile.dto.ProfileDocumentImportRequest;
import com.careertuner.profile.dto.ProfileImportResponse;
import com.careertuner.profile.dto.UserProfileRequest;
import com.careertuner.profile.dto.UserProfileResponse;
import com.careertuner.profile.dto.UserProfileVersionResponse;

public interface ProfileService {

    UserProfileResponse me(AuthUser authUser);

    UserProfileResponse save(AuthUser authUser, UserProfileRequest request);

    /** 현재 사용자의 불변 프로필 스냅샷 이력. */
    List<UserProfileVersionResponse> versions(AuthUser authUser, int limit);

    UserProfileVersionResponse version(AuthUser authUser, Long versionId);

    default ProfileAiResponse summarize(AuthUser authUser) {
        return summarize(authUser, RequestedAiModel.AUTO);
    }

    /** 사용자가 AI 모델을 명시 선택하는 프로필 요약(기본 AUTO=현행 폴백). */
    ProfileAiResponse summarize(AuthUser authUser, RequestedAiModel requestedModel);

    /** 저장된 프로필 AI 분석 산출물 조회(feature 별 최신). 분석 이력이 없으면 빈 결과. */
    com.careertuner.profile.dto.ProfileAiAnalysisResponse aiAnalysis(AuthUser authUser);

    default ProfileAiResponse extractSkills(AuthUser authUser) {
        return extractSkills(authUser, RequestedAiModel.AUTO);
    }

    ProfileAiResponse extractSkills(AuthUser authUser, RequestedAiModel requestedModel);

    default ProfileCompletenessResponse diagnoseCompleteness(AuthUser authUser) {
        return diagnoseCompleteness(authUser, RequestedAiModel.AUTO);
    }

    ProfileCompletenessResponse diagnoseCompleteness(AuthUser authUser, RequestedAiModel requestedModel);

    List<UserProfileResponse> adminProfiles(AuthUser authUser, String keyword, int limit);

    UserProfileResponse adminProfile(AuthUser authUser, Long userId);

    List<UserProfileVersionResponse> adminVersions(AuthUser authUser, Long userId, int limit);

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
