package com.careertuner.planner.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PlannerReminderSoftDeleteContractTest {

    @Test
    void reminderReplacementCancelsOldRowsInsteadOfDeletingHistory() throws IOException {
        String xml;
        try (var input = getClass().getResourceAsStream("/mapper/planner/PlannerMapper.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml).contains("<update id=\"cancelRemindersByItem\">")
                .contains("SET psr.status = 'CANCELED'")
                .contains("AND status != 'CANCELED'")
                .doesNotContain("DELETE psr")
                .doesNotContain("<delete id=\"deleteRemindersByItem\">");
    }
}
