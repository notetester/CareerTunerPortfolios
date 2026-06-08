package com.careertuner.admin.fitanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFitAnalysisMemo {

    private Long id;
    private Long fitAnalysisId;
    private Long adminUserId;
    private String adminName;
    private String adminEmail;
    private String memoType;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
