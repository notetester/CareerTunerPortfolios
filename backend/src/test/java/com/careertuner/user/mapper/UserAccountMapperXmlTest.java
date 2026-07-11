package com.careertuner.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UserAccountMapperXmlTest {

    @Test
    void socialUnlinkLocksStableUserRowBeforeCheckingLoginMethods() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/user/UserAccountMapper.xml"));
        int start = xml.indexOf("<select id=\"findByIdForUpdate\"");
        int end = xml.indexOf("</select>", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        assertThat(xml.substring(start, end))
                .contains("FROM users")
                .contains("id = #{id}")
                .contains("FOR UPDATE");
    }

    @Test
    void socialProviderUniquenessIsPresentInSchemaAndIdempotentPatch() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260711_user_social_provider_unique.sql"));

        assertThat(schema).contains(
                "UNIQUE KEY uk_user_social_user_provider (user_id, provider)");
        assertThat(patch)
                .contains("DELETE duplicate_social")
                .contains("MIN(id) AS keep_id")
                .contains("GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)")
                .contains("user_id,provider")
                .contains("ADD UNIQUE INDEX uk_user_social_user_provider");
    }
}
