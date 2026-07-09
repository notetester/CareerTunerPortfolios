package com.careertuner.applicationcase.dto;

import com.careertuner.jobposting.dto.JobPostingResponse;

public record ApplicationCaseFromJobPostingResponse(
        ApplicationCaseResponse applicationCase,
        JobPostingResponse jobPosting,
        JobPostingMetadataResponse metadata,
        ApplicationCaseExtractionResponse extractionJob
) {
}
