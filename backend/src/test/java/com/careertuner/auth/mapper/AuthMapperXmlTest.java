package com.careertuner.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AuthMapperXmlTest {

    private static final Path MAPPER = Path.of("src/main/resources/mapper/auth/AuthMapper.xml");

    @Test
    void loginPermissionFallbackExcludesInactivePoliciesAndGroups() throws Exception {
        String xml = Files.readString(MAPPER);
        String permissionSelect = selectBody(xml, "findActivePermissionCodes");
        String groupSelect = selectBody(xml, "findActivePermissionGroups");

        assertThat(permissionSelect)
                .contains("JOIN admin_permission_policy policy")
                .contains("policy.active = 1")
                .contains("assignment.revoked_at IS NULL");
        assertThat(groupSelect)
                .contains("JOIN admin_permission_group permission_group")
                .contains("permission_group.active = 1")
                .contains("assignment.revoked_at IS NULL");
    }

    @Test
    void nativeHandoffExchangeUsesRowLockAndConditionalSingleConsume() throws Exception {
        String xml = Files.readString(MAPPER);
        String lockedSelect = selectBody(xml, "findNativeAuthHandoffForUpdate");
        String consumeDelete = deleteBody(xml, "consumeNativeAuthHandoff");
        String cleanupDelete = deleteBody(xml, "deleteExpiredNativeAuthHandoffs");

        assertThat(lockedSelect)
                .contains("WHERE code_hash = #{codeHash}")
                .contains("FOR UPDATE");
        assertThat(consumeDelete)
                .contains("DELETE FROM native_auth_handoff")
                .contains("expired_at &gt; NOW()");
        assertThat(cleanupDelete)
                .contains("expired_at &lt;= NOW()")
                .contains("LIMIT 100");
        assertThat(xml).contains("DATE_ADD(NOW(), INTERVAL 3 MINUTE)");
        assertThat(xml).contains("email, email_verified, display_name");
    }

    @Test
    void nativeHandoffSchemaAndPatchStoreOnlyCodeHash() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260712_native_auth_handoff.sql"));

        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS native_auth_handoff")
                .contains("code_hash         CHAR(43) CHARACTER SET ascii COLLATE ascii_bin")
                .contains("provider_user_id  VARCHAR(255) NOT NULL")
                .contains("email             VARCHAR(255) NULL")
                .contains("email_verified    TINYINT(1)   NOT NULL DEFAULT 0")
                .contains("display_name      VARCHAR(100) NULL")
                .contains("UNIQUE KEY uk_native_auth_handoff_code_hash");
        assertThat(patch).contains("CREATE TABLE IF NOT EXISTS native_auth_handoff")
                .contains("code_hash         CHAR(43) CHARACTER SET ascii COLLATE ascii_bin")
                .contains("email_verified    TINYINT(1)   NOT NULL DEFAULT 0")
                .contains("COLUMN_NAME IN ('user_id', 'consumed_at')")
                .contains("COLUMN_NAME = 'email_verified'")
                .contains("'DROP TABLE native_auth_handoff'")
                .doesNotContain("handoff_code         ")
                .doesNotContain("user_id           BIGINT");
    }

    private static String selectBody(String xml, String selectId) {
        int start = xml.indexOf("<select id=\"" + selectId + "\"");
        assertThat(start).as(selectId + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</select>", start));
    }

    private static String deleteBody(String xml, String deleteId) {
        int start = xml.indexOf("<delete id=\"" + deleteId + "\"");
        assertThat(start).as(deleteId + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</delete>", start));
    }
}
