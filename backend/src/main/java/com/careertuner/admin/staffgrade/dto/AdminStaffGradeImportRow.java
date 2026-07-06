package com.careertuner.admin.staffgrade.dto;

import lombok.Data;

/** Excel 업로드 미리보기/확정 행. status: OK/ERROR. */
@Data
public class AdminStaffGradeImportRow {
    private int rowNumber;
    private String email;
    private Long userId;
    private String userName;
    private String department;
    private String seniority;
    private String jobTier;
    private String payBand;
    private String jobGrade;
    private String payStep;
    private Integer baseSalary;
    private String currency;
    private String effectiveDate;
    private String status;
    private String message;
}
