package com.careertuner.ai.autoprep.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AutoPrepCaseDedupeMapperXmlTest {

    private static final Path XML = Path.of(
            "src/main/resources/mapper/ai/AutoPrepCaseDedupeMapper.xml");

    @Test
    void reservationUsesUniqueKeyInsertAndLoserReadsCommittedBindingWithLock() throws Exception {
        String xml = Files.readString(XML);

        assertThat(xml).contains("INSERT IGNORE INTO auto_prep_case_dedupe");
        assertThat(xml).contains("(user_id, file_id, application_case_id)");
        assertThat(statement(xml, "<select id=\"findApplicationCaseIdForUpdate\"", "</select>"))
                .contains("application_case_id")
                .contains("user_id = #{userId}")
                .contains("file_id = #{fileId}")
                .contains("FOR UPDATE");
    }

    @Test
    void bindAndReleaseOnlyTouchUnboundReservation() throws Exception {
        String xml = Files.readString(XML);

        assertThat(statement(xml, "<update id=\"bindApplicationCase\"", "</update>"))
                .contains("SET application_case_id = #{applicationCaseId}")
                .contains("application_case_id IS NULL");
        assertThat(statement(xml, "<delete id=\"deleteUnboundReservation\"", "</delete>"))
                .contains("application_case_id IS NULL");
    }

    @Test
    void schemaAndPatchPersistMappingAfterPendingFileDeletion() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260714_auto_prep_case_dedupe.sql"));

        for (String ddl : new String[] { schema, patch }) {
            int start = ddl.indexOf("CREATE TABLE IF NOT EXISTS auto_prep_case_dedupe");
            assertThat(start).isGreaterThanOrEqualTo(0);
            String table = ddl.substring(start, ddl.indexOf(") ENGINE", start));
            assertThat(table).contains("PRIMARY KEY (user_id, file_id)");
            assertThat(table).contains("application_case_id BIGINT NULL");
            assertThat(table).doesNotContain("FOREIGN KEY (file_id)");
        }
        assertThat(patch).contains("file_asset").contains("의도적으로 FK를 두지 않는다");
    }

    private static String statement(String xml, String openTag, String closeTag) {
        int start = xml.indexOf(openTag);
        assertThat(start).isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf(closeTag, start));
    }
}
