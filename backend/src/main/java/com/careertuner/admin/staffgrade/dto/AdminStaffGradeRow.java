package com.careertuner.admin.staffgrade.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** 등급/급여 목록 행(users 조인). */
@Data
public class AdminStaffGradeRow {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private String userRole;
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
    private LocalDateTime updatedAt;
}
