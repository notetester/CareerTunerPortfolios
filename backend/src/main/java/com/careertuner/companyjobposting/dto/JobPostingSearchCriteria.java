package com.careertuner.companyjobposting.dto;

/**
 * 공개 게시판 검색 조건(서비스에서 정규화 후 매퍼로 전달).
 * sort 는 latest/deadline/views 화이트리스트, offset = page * size.
 */
public record JobPostingSearchCriteria(
        String keyword,
        String jobRole,
        String location,
        String employmentType,
        String careerLevel,
        String sort,
        int page,
        int size,
        int offset
) {
}
