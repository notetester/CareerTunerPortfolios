package com.careertuner.profile.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분석 재현성을 위한 불변 프로필 스냅샷. 현재값(user_profile)을 덮어써도
 * 과거 AI 분석이 참조한 입력은 이 행으로 복원할 수 있다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVersion {

    private Long id;
    private Long userId;
    private Integer versionNo;
    private String desiredJob;
    private String desiredIndustry;
    private String education;
    private String career;
    private String projects;
    private String skills;
    private String certificates;
    private String languages;
    private String portfolioLinks;
    private String resumeText;
    private String selfIntro;
    private String preferences;
    private String source;
    private LocalDateTime createdAt;
}
