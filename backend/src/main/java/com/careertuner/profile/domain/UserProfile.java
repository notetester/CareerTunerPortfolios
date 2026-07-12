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
    /** DB 컬럼이 아닌 요청 단위 보조 컨텍스트. AutoPrep가 연결된 PORTFOLIO 파일을 별도 의미로 AI에 전달한다. */
    private String portfolioEvidence;
    private String resumeText;
    private String selfIntro;
    private String preferences;
    /** 사용자별 프로필 스냅샷 버전. 저장할 때마다 증가한다. */
    private Integer versionNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
