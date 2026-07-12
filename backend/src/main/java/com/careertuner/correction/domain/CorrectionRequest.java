package com.careertuner.correction.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionRequest {

    private Long id;
    private Long userId;
    private String requestKey;
    private Long applicationCaseId;
    private String correctionType;
    private String sourceType;
    private Long sourceRefId;
    private String originalText;
    private String improvedText;
    private String resultJson;
    private String sourceSnapshot;
    private String status;
    private Long aiUsageLogId;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
}
