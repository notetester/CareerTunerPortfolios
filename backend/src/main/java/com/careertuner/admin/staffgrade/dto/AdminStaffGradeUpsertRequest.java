package com.careertuner.admin.staffgrade.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 등급/급여 편집(수동). */
public record AdminStaffGradeUpsertRequest(
        @Size(max = 60) String department,
        @Size(max = 30) String seniority,
        @Size(max = 30) String jobTier,
        @Size(max = 30) String payBand,
        @Size(max = 30) String jobGrade,
        @Size(max = 30) String payStep,
        @PositiveOrZero Integer baseSalary,
        @Size(max = 10) String currency,
        LocalDate effectiveDate,
        @Size(max = 255) String memo
) {
}
