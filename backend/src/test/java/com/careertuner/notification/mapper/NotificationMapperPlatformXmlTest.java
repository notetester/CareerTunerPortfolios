package com.careertuner.notification.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class NotificationMapperPlatformXmlTest {

    private static final Path MAPPER = Path.of(
            "src/main/resources/mapper/notification/NotificationMapper.xml");

    @Test
    void platformQueriesIncludeAllAndRequestedDestinationOnly() throws Exception {
        String xml = Files.readString(MAPPER);

        assertThat(selectBody(xml, "findByUserId"))
                .contains("COALESCE(n.destination_platform, 'ALL') IN ('ALL', #{platform})")
                .contains("platform != null");
        assertThat(selectBody(xml, "countByUserId"))
                .contains("COALESCE(n.destination_platform, 'ALL') IN ('ALL', #{platform})");
        assertThat(selectBody(xml, "countUnreadByUserId"))
                .contains("COALESCE(n.destination_platform, 'ALL') IN ('ALL', #{platform})");
    }

    @Test
    void insertSchemaAndIdempotentPatchPersistCanonicalDestination() throws Exception {
        String xml = Files.readString(MAPPER);
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260712_notification_destination_platform.sql"));

        assertThat(xml).contains("destination_platform, title, message, link")
                .contains("COALESCE(#{destinationPlatform}, 'ALL')");
        assertThat(schema)
                .contains("destination_platform ENUM('ALL', 'MOBILE', 'DESKTOP') NOT NULL DEFAULT 'ALL'")
                .contains("idx_notification_user_platform_unread");
        assertThat(patch)
                .contains("COLUMN_NAME = 'destination_platform'")
                .contains("@ct_notification_destination_column_exists = 0")
                .contains("idx_notification_user_platform_unread")
                .contains("'PASS', 'FAIL'");
    }

    private static String selectBody(String xml, String selectId) {
        int start = xml.indexOf("<select id=\"" + selectId + "\"");
        assertThat(start).as(selectId + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</select>", start));
    }
}
