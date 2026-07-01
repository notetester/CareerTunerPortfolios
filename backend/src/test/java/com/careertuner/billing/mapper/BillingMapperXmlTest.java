package com.careertuner.billing.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BillingMapperXmlTest {

    @Test
    void paymentHistoryReturnsOnlyPaidOrCanceledPayments() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/billing/BillingMapper.xml"));
        int start = xml.indexOf("<select id=\"findPaymentsByUserId\"");
        int end = xml.indexOf("</select>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);

        String query = xml.substring(start, end);
        assertThat(query).contains("status IN ('PAID', 'CANCELED')");
        assertThat(query).doesNotContain("'READY'");
    }
}
