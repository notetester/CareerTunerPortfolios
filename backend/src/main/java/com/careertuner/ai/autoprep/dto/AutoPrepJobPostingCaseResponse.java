package com.careertuner.ai.autoprep.dto;

/** AutoPrep 전용 PDF/이미지 공고 업로드가 만든(또는 멱등 재사용한) 지원 건 식별자. */
public record AutoPrepJobPostingCaseResponse(Long applicationCaseId) {
}
