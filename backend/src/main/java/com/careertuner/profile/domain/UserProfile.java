package com.careertuner.profile.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    private Long id;
    private Long userId;
    private String desiredJob;
    private String desiredIndustry;
    private String education;
    private String career;
    private String projects;
    private String skills;
    private String certificates;
    private String languages;
    private String portfolioLinks;
    private String jobPreferences;
    private String personalInfo;
    private String activities;
    private String accountLinks;
    private String chatProfiles;
    private String resumeText;
    private String selfIntro;
    private String preferences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String loginId;
    private String phoneNumber;
    private boolean phoneVerified;
}
