package com.careertuner.admin.correction.dto;

import java.util.List;

public record AdminCorrectionPage(
        List<AdminCorrectionRow> items,
        long total,
        int page,
        int size
) {
}
