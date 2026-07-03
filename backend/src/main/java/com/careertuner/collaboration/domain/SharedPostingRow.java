package com.careertuner.collaboration.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedPostingRow {

    private Long id;
    private Long messageId;
    private Long applicationCaseId;
    private String companyName;
    private String jobTitle;
    private LocalDate deadlineDate;
    private String sourceType;
    private LocalDateTime createdAt;
}
