package com.careertuner.applicationcase.domain;

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
public class ApplicationCase {

    private Long id;
    private Long userId;
    private String companyName;
    private String jobTitle;
    private LocalDate postingDate;
    private String sourceType;
    private String status;
    private boolean favorite;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
