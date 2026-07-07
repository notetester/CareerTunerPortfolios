package com.careertuner.companyjobposting.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 관리자 공고 검토 큐 행. reviewType: CREATE(신규 등록) / UPDATE(게시 중 수정본). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingReviewRow {

    private String reviewType;
    private Long postingId;
    private Long revisionId;
    private String title;
    private String jobRole;
    private String companyName;
    private String trustGrade;
    private LocalDateTime submittedAt;
}
