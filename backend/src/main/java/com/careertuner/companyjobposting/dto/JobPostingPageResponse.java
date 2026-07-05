package com.careertuner.companyjobposting.dto;

import java.util.List;

/** 공개 게시판 목록 페이지 응답. */
public record JobPostingPageResponse(
        List<CompanyJobPostingResponse> items,
        long total,
        int page,
        int size
) {
}
