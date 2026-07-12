package com.careertuner.profile.domain;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 프로필 AI 분석 산출물 영속 행(A영역). feature_type 별 최신 1행. JSON 컬럼은 문자열로 보관하고
 * 서비스 계층에서 직렬화/역직렬화한다(다른 도메인의 fit_analysis JSON 컬럼 패턴과 동일).
 */
@Data
@Builder
public class ProfileAiAnalysis {

    private Long id;
    private Long userId;
    /** 이 분석이 실제로 사용한 user_profile_version.id. */
    private Long profileVersionId;
    private String featureType;
    private String summary;
    private String strengths;         // JSON 문자열 ["..."]
    private String gaps;              // JSON 문자열 ["..."]
    private String recommendations;   // JSON 문자열 ["..."]
    private String extractedSkills;   // JSON 문자열 ["..."]
    private String criteria;          // JSON 문자열 [{...}]
    private String jobFamily;
    private Integer completenessScore;
    private Integer aiScore;
    private String qualityWarnings;   // JSON 문자열 ["..."]
    private String model;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
