package com.careertuner.jobanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAnalysis {

    private Long id;
    private Long applicationCaseId;
    private String employmentType;
    private String experienceLevel;
    private String requiredSkills;
    private String preferredSkills;
    private String duties;
    private String qualifications;
    private String difficulty;
    private String summary;
    private LocalDateTime createdAt;
}
