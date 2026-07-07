package com.careertuner.admin.staffgrade.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** 미리보기에서 확인한 행들을 확정 적용. */
public record AdminStaffGradeImportApplyRequest(
        @NotNull List<AdminStaffGradeImportRow> rows
) {
}
