package com.careertuner.fitanalysis.domain;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 적합도 분석 생성을 위한 입력 원천(읽기 전용 조인 결과).
 *
 * <p>application_case(B), job_analysis(B), user_profile(A)를 조인해 가져오며 C는 어떤 원본도 수정하지 않는다.
 * JSON 컬럼(required_skills 등)은 원문 JSON 문자열 그대로 담고, 파싱은 서비스 계층에서 한다.
 */
@Data
public class FitAnalysisGenerationSource {

    private Long jobAnalysisId;
    private Long jobPostingId;
    private Integer jobPostingRevision;
    private LocalDateTime jobAnalysisCreatedAt;
    private Long userProfileId;
    /** 실제 비교 입력으로 사용한 불변 user_profile_version.id. */
    private Long profileVersionId;
    private Integer profileVersionNo;
    private LocalDateTime profileUpdatedAt;
    private String companyName;
    private String jobTitle;
    private String requiredSkills;        // job_analysis.required_skills (JSON)
    private String preferredSkills;       // job_analysis.preferred_skills (JSON)
    private String duties;                // job_analysis.duties
    private String profileSkills;         // user_profile.skills (JSON)
    private String profileCertificates;   // user_profile.certificates (JSON)
    private String desiredJob;            // user_profile.desired_job
    // B(company_analysis) 기업 맥락 — 설명 생성(strategy) 참고용. 판단값 계산엔 미사용(뉴로-심볼릭 불변식).
    private String companySummary;        // company_analysis.company_summary
    private String recentIssues;          // company_analysis.recent_issues
    private String interviewPoints;       // company_analysis.interview_points
}
