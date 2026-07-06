package com.careertuner.admin.staffgrade.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 등급·급여 변경 이력(admin_staff_grade_history). old/new 스냅샷 JSON. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStaffGradeHistory {
    private Long id;
    private Long userId;
    private String oldValuesJson;
    private String newValuesJson;
    private Long changedBy;
    private String source;
    private String memo;
    private LocalDateTime createdAt;
}
