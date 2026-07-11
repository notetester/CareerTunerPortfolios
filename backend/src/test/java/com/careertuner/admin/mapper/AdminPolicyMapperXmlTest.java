package com.careertuner.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminPolicyMapperXmlTest {

    @Test
    void systemPolicyUsesPolicyCodeNaturalKeyWithoutMissingIdColumn() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/admin/policy/AdminPolicyMapper.xml"));
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertThat(xml)
                .contains("SELECT policy_code, display_name, description")
                .doesNotContain("SELECT id, policy_code");
        assertThat(schema)
                .contains("CREATE TABLE IF NOT EXISTS `admin_system_policy`")
                .contains("PRIMARY KEY (`policy_code`)");
    }
}
