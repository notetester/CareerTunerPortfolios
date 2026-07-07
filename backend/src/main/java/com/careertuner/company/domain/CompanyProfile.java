package com.careertuner.company.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 승인된 기업 프로필(users 1:1). trust_grade: BASIC/VERIFIED/PARTNER. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfile {

    private Long id;
    private Long userId;
    private String companyName;
    private String businessNumber;
    private String trustGrade;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
