package com.careertuner.applicationcase.dto;

import jakarta.validation.constraints.Size;

public record CreateApplicationCaseFromJobPostingRequest(
        String originalText,
        @Size(max = 512) String uploadedFileUrl,
        String extractedText,
        @Size(max = 20) String sourceType,
        Boolean favorite,
        @Size(max = 20) String jobAnalysisProvider,
        @Size(max = 20) String companyAnalysisProvider
) {

    /**
     * 모델 선택 없이 등록하는 기존 호출 호환(공고분석·기업분석 provider = null → 현행 기본 체인).
     * 이 5-arg 보조 생성자 덕에 기존 호출부는 수정 없이 그대로 컴파일된다.
     */
    public CreateApplicationCaseFromJobPostingRequest(String originalText,
                                                      String uploadedFileUrl,
                                                      String extractedText,
                                                      String sourceType,
                                                      Boolean favorite) {
        this(originalText, uploadedFileUrl, extractedText, sourceType, favorite, null, null);
    }
}
