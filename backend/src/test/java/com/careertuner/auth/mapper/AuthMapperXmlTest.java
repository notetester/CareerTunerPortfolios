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

    private static String selectBody(String xml, String selectId) {
        int start = xml.indexOf("<select id=\"" + selectId + "\"");
        assertThat(start).as(selectId + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</select>", start));
    }
}
