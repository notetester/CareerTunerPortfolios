package com.careertuner.admin.staffgrade.dto;

import java.util.List;

public record AdminStaffGradeImportPreview(
        int totalRows,
        int okCount,
        int errorCount,
        List<AdminStaffGradeImportRow> rows
) {
}
