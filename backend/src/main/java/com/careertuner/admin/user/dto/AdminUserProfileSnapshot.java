package com.careertuner.admin.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminUserProfileSnapshot {
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
    private String resumeText;
    private String selfIntro;
    private String preferences;
    private LocalDateTime updatedAt;
}
