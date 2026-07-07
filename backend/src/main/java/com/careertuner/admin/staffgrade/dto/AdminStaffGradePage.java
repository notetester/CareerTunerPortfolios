package com.careertuner.admin.staffgrade.dto;

import java.util.List;

public record AdminStaffGradePage(
        List<AdminStaffGradeRow> items,
        long total,
        int page,
        int size
) {
}
