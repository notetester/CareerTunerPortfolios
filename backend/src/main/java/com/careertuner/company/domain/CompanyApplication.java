package com.careertuner.company.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 기업 계정 전환 신청 1건. status: PENDING/APPROVED/REJECTED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyApplication {

    private Long id;
    private Long userId;
    private String companyName;
    private String businessNumber;
    private String contact;
    private String description;
    private String status;
    private String rejectReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 관리자 목록 JOIN 컬럼
    private String applicantEmail;
    private String applicantName;
}
