package com.careertuner.interview.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InterviewDerivedLifecycleMapperXmlTest {

    private static final Path INTERVIEW_XML =
            Path.of("src/main/resources/mapper/interview/InterviewMapper.xml");
    private static final Path MEDIA_XML =
            Path.of("src/main/resources/mapper/interview/InterviewMediaMapper.xml");
    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path PATCH =
            Path.of("src/main/resources/db/patches/20260712_interview_derived_lifecycle.sql");

    @Test
    void questionReplacementIsLockedAndBlockedByEveryDerivedDataClass() throws Exception {
        String xml = Files.readString(INTERVIEW_XML);
        assertThat(statement(xml, "select", "lockSessionByIdAndUserId")).contains("FOR UPDATE");
        assertThat(statement(xml, "select", "lockQuestionByIdAndUserId")).contains("FOR UPDATE");
        assertThat(statement(xml, "select", "hasQuestionRegenerationBlockers"))
                .contains("interview_answer")
                .contains("file_asset")
                .contains("interview_media_analysis")
                .contains("interview_agent_step");
        assertThat(statement(xml, "update", "invalidateSessionResult"))
                .contains("total_score = NULL")
                .contains("report = NULL")
                .contains("ended_at = NULL");
    }

    @Test
    void mediaSchemaAndUpsertKeepSessionRowsCompatibleAndAnswerRowsIdempotent() throws Exception {
        String schema = Files.readString(SCHEMA);
        String patch = Files.readString(PATCH);
        String media = Files.readString(MEDIA_XML);

        assertThat(schema)
                .contains("`question_id` bigint DEFAULT NULL")
                .contains("`answer_id` bigint DEFAULT NULL")
                .contains("UNIQUE KEY `uk_media_analysis_answer_kind` (`answer_id`,`kind`)")
                .contains("CONSTRAINT `fk_media_analysis_answer`");
        assertThat(patch)
                .contains("information_schema.COLUMNS")
                .contains("ADD UNIQUE INDEX uk_media_analysis_answer_kind")
                .contains("CHECK (guard_ok = 1)")
                .contains("'PASS', 'FAIL'");
        assertThat(statement(media, "insert", "insertMediaAnalysis"))
                .contains("question_id, answer_id")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("LAST_INSERT_ID(id)");
    }

    private static String statement(String xml, String element, String id) {
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).as(id).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).as(id).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
