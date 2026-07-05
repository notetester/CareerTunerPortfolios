package com.careertuner.companyjobposting.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 게시 중 공고의 수정 검토용 변경본. payload_json 에 전체 필드가 담긴다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyJobPostingRevision {

    private Long id;
    private Long jobPostingId;
    private String payloadJson;
    private String status;
    private String rejectReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
