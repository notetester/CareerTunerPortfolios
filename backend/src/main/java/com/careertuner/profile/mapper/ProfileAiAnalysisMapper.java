package com.careertuner.profile.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.profile.domain.ProfileAiAnalysis;

@Mapper
public interface ProfileAiAnalysisMapper {

    /** feature_type 별 최신 1행 upsert(user_id+feature_type unique). */
    void upsert(ProfileAiAnalysis analysis);

    /** 사용자의 모든 feature 최신 분석(조회 API용). */
    List<ProfileAiAnalysis> findByUserId(@Param("userId") Long userId);

    /** 특정 feature 최신 분석(C 입력용 — PROFILE_SUMMARY). 없으면 null. */
    ProfileAiAnalysis findByUserIdAndFeature(@Param("userId") Long userId,
                                             @Param("featureType") String featureType);
}
