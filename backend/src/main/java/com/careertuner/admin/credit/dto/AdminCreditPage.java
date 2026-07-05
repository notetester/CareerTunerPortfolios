package com.careertuner.admin.credit.dto;

import java.util.List;

public record AdminCreditPage(
        List<AdminCreditTransactionRow> items,
        long total,
        int page,
        int size
) {
}
