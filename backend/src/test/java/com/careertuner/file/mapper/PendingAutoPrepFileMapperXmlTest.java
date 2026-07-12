package com.careertuner.file.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PendingAutoPrepFileMapperXmlTest {

    private static final Path XML = Path.of("src/main/resources/mapper/file/FileAssetMapper.xml");

    @Test
    void selectionAndDeleteBothRequireExactPendingPurposeAndTtl() throws Exception {
        String xml = Files.readString(XML);
        String select = statement(xml, "select", "findStalePendingAutoPrepAttachments");
        String delete = statement(xml, "delete", "deleteStalePendingAutoPrepAttachment");

        assertThat(select)
                .contains("kind = 'ATTACHMENT'")
                .contains("ref_type = 'AUTO_PREP_PENDING'")
                .contains("ref_id IS NULL")
                .contains("created_at &lt; #{cutoff}")
                .contains("LIMIT #{limit}");
        assertThat(delete)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("kind = 'ATTACHMENT'")
                .contains("ref_type = 'AUTO_PREP_PENDING'")
                .contains("ref_id IS NULL")
                .contains("created_at &lt; #{cutoff}");
    }

    private static String statement(String xml, String element, String id) {
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
