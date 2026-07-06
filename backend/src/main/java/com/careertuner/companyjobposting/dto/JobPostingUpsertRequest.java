package com.careertuner.companyjobposting.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공고 작성/수정 요청 — 사람인/잡코리아식 상세 필드 전부.
 * 수정 검토(revision) payload JSON 으로도 그대로 직렬화된다.
 *
 * @param submit true 면 저장과 동시에 제출(신뢰등급 정책에 따라 검토 대기 또는 즉시 게시)
 */
public record JobPostingUpsertRequest(
        @NotBlank(message = "공고 제목을 입력해 주세요.") @Size(max = 255) String title,
        @NotBlank(message = "직무명을 입력해 주세요.") @Size(max = 100) String jobRole,
        @Size(max = 30) String employmentType,
        @Size(max = 30) String careerLevel,
        Integer careerYearsMin,
        Integer careerYearsMax,
        @Size(max = 30) String educationLevel,
        @Size(max = 100) String salaryText,
        Boolean salaryNegotiable,
        @Size(max = 255) String workLocation,
        @Size(max = 100) String workHours,
        LocalDate deadlineDate,
        Boolean alwaysOpen,
        String mainTasks,
        String requirements,
        String preferred,
        String benefits,
        String hiringProcess,
        @Size(max = 50) String headcount,
        List<String> tags,
        Boolean submit
) {
}
