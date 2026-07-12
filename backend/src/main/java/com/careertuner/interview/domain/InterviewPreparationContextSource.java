package com.careertuner.interview.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 면접 질문 생성에 사용하는 A/B/C 최신 정본의 읽기 전용 조인 결과.
 *
 * <p>원천 행은 모두 append-only 또는 버전 행이며, {@code interview_session.source_snapshot}에는
 * 이 행들의 식별자와 C 적합도 핵심 결과를 남겨 이후 평가·리포트가 같은 근거를 재사용한다.
 */
@Data
public class InterviewPreparationContextSource {

    private Long profileVersionId;
    private Integer profileVersionNo;
    private String desiredJob;
    private String education;
    private String career;
    private String projects;
    private String skills;
    private String certificates;
    private String portfolioLinks;
    private String resumeText;
    private String selfIntro;

    private Long jobAnalysisId;
    private String requiredSkills;
    private String preferredSkills;
    private String duties;
    private String qualifications;
    private String jobSummary;

    private Long companyAnalysisId;
    private String companySummary;
    private String recentIssues;
    private String interviewPoints;

    private Long fitAnalysisId;
    private Integer fitScore;
    private String matchedSkills;
    private String missingSkills;
    private String gapRecommendations;
    private String strategyActions;
    private String fitModel;
    private String fitPromptVersion;
    private LocalDateTime fitCreatedAt;
}
