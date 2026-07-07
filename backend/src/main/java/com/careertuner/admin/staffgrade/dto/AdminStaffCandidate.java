package com.careertuner.admin.staffgrade.dto;

/** 등급 배정 대상 후보(관리자/직원 계정). */
public record AdminStaffCandidate(
        Long userId,
        String email,
        String name,
        String role,
        boolean hasGrade
) {
}
