package com.careertuner.admin.staffgrade.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 관리자/직원 조직 등급 및 급여(admin_staff_grade). base_salary 는 민감정보. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStaffGrade {
    private Long id;
    private Long userId;
    private String department;
    private String seniority;
    private String jobTier;
    private String payBand;
    private String jobGrade;
    private String payStep;
    private Integer baseSalary;
    private String currency;
    private LocalDate effectiveDate;
    private String memo;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
