package com.careertuner.notification.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PushSubscriptionAccountStateXmlTest {

    @Test
    void subscriptionWritesAndReadsRequireActiveAccount() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/notification/PushSubscriptionMapper.xml"));

        assertThat(statement(xml, "insert", "upsert"))
                .contains("FROM users u")
                .contains("u.id = #{userId}")
                .contains("u.status = 'ACTIVE'");
        assertThat(statement(xml, "select", "findByUserId"))
                .contains("JOIN users u")
                .contains("u.status = 'ACTIVE'");
        assertThat(statement(xml, "select", "countByUserId"))
                .contains("JOIN users u")
                .contains("u.status = 'ACTIVE'");
        assertThat(statement(xml, "delete", "deleteAllByUserId"))
                .contains("user_id = #{userId}");
    }

    private static String statement(String xml, String tag, String id) {
        int start = xml.indexOf("<" + tag + " id=\"" + id + "\"");
        int end = xml.indexOf("</" + tag + ">", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
