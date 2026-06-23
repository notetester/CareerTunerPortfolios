package com.careertuner.admin.legal.mapper;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 버전 목록 행 (조항 수 포함). MyBatis 평면 컬럼 매핑용 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVersionRow {

    private Long id;
    private String docType;
    private String versionLabel;
    private String status;
    private String summary;
    private boolean adverse;
    private LocalDateTime effectiveDate;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int clauseCount;
}
