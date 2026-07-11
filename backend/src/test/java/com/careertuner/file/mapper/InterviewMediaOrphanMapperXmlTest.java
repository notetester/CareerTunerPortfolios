package com.careertuner.file.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InterviewMediaOrphanMapperXmlTest {

    private static final Path XML = Path.of("src/main/resources/mapper/file/FileAssetMapper.xml");

    @Test
    void orphanSelectionAndDeleteBothRecheckMissingAnswerAndTtl() throws Exception {
        String xml = Files.readString(XML);
        String select = statement(xml, "select", "findStaleOrphanedInterviewMedia");
        String delete = statement(xml, "delete", "deleteStaleOrphanedInterviewMedia");

        assertThat(select)
                .contains("f.ref_type = 'INTERVIEW_ANSWER'")
                .contains("f.ref_id IS NOT NULL")
                .contains("f.created_at &lt; #{cutoff}")
                .contains("NOT EXISTS")
                .contains("a.id = f.ref_id")
                .contains("LIMIT #{limit}");
        assertThat(delete)
                .contains("f.owner_user_id = #{ownerUserId}")
                .contains("f.created_at &lt; #{cutoff}")
                .contains("NOT EXISTS")
                .contains("a.id = f.ref_id");
    }

    private static String statement(String xml, String element, String id) {
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
