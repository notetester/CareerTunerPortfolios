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
        assertThat(statementBody(xml, "update", "markAllAsRead"))
                .contains("COALESCE(destination_platform, 'ALL') IN ('ALL', #{platform})")
                .contains("platform != null");
        assertThat(statementBody(xml, "update", "deleteAllByUser"))
                .contains("COALESCE(destination_platform, 'ALL') IN ('ALL', #{platform})")
                .contains("platform != null")
                .contains("deleted_at = COALESCE(deleted_at, NOW())");
    }

    @Test
    void insertSchemaAndIdempotentPatchesPersistCanonicalDestination() throws Exception {
        String xml = Files.readString(MAPPER);
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String initialPatch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260712_notification_destination_platform.sql"));
        String webPatch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260712_notification_web_destination.sql"));

        assertThat(xml).contains("destination_platform, title, message, link")
                .contains("COALESCE(#{destinationPlatform}, 'ALL')");
        assertThat(statementBody(xml, "insert", "insert"))
                .contains("FROM users recipient")
                .contains("recipient.id = #{userId}")
                .contains("recipient.status = 'ACTIVE'");
        assertThat(schema)
                .contains("destination_platform ENUM('ALL', 'MOBILE', 'DESKTOP', 'WEB') NOT NULL DEFAULT 'ALL'")
                .contains("idx_notification_user_platform_unread");
        assertThat(initialPatch)
                .contains("COLUMN_NAME = 'destination_platform'")
                .contains("@ct_notification_destination_column_exists = 0")
                .contains("ENUM(''ALL'', ''MOBILE'', ''DESKTOP'', ''WEB'')")
                .contains("destination_platform NOT IN ('ALL', 'MOBILE', 'DESKTOP', 'WEB')")
                .contains("idx_notification_user_platform_unread")
                .contains("'PASS', 'FAIL'");
        assertThat(webPatch)
                .contains("ENUM(''ALL'', ''MOBILE'', ''DESKTOP'', ''WEB'')")
                .contains("LOWER(COLUMN_TYPE) = 'enum(''all'',''mobile'',''desktop'',''web'')'")
                .contains("destination_platform NOT IN ('ALL', 'MOBILE', 'DESKTOP', 'WEB')")
                .contains("ct_notification_destination_web_guard")
                .contains("'PASS', 'FAIL'");
    }

    private static String selectBody(String xml, String selectId) {
        int start = xml.indexOf("<select id=\"" + selectId + "\"");
        assertThat(start).as(selectId + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</select>", start));
    }

    private static String statementBody(String xml, String tag, String id) {
        int start = xml.indexOf("<" + tag + " id=\"" + id + "\"");
        assertThat(start).as(id + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</" + tag + ">", start));
    }
}
