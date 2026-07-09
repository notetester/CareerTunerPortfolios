package com.careertuner.community.domain;

import lombok.Getter;

@Getter
public enum PostCategory {

    JOB_REVIEW("취업후기"),
    RECOMMENDED_JOB("채용공고"),
    INTERVIEW_REVIEW("면접후기"),
    JOB_QUESTION("직무질문"),
    SUCCESS_STRATEGY("합격전략"),
    PORTFOLIO_FEEDBACK("포트폴리오"),
    CERTIFICATE_REVIEW("자격증후기"),
    FREE("자유게시판");

    private final String label;

    PostCategory(String label) {
        this.label = label;
    }
}
