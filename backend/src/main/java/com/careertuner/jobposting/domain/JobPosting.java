package com.careertuner.jobposting.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPosting {

    private Long id;
    private Long applicationCaseId;
    private Integer revision;
    private String originalText;
    private String uploadedFileUrl;
    private String extractedText;
    private String sourceType;
    private LocalDateTime createdAt;
}
