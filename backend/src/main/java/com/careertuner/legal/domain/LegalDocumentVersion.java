package com.careertuner.legal.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 법적 문서 버전 (legal_document_version).
 * status = DRAFT | PUBLISHED. 게시중/예정/종료 배지는 effective_date vs NOW() 로 계산한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalDocumentVersion {

    private Long id;
    private String docType;        // LegalDocType enum name
    private String versionLabel;   // 표시용 버전 (예: v2.4)
    private String status;         // DRAFT | PUBLISHED
    private String summary;        // 개정 요약 (AI 자동생성 가능)
    private boolean adverse;       // is_adverse (불리한 변경)
    private LocalDateTime effectiveDate;
    private LocalDateTime publishedAt;
    private Long adminId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
