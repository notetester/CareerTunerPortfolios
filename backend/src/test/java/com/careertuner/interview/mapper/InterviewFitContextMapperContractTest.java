package com.careertuner.interview.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InterviewFitContextMapperContractTest {

    private static final Path XML = Path.of("src/main/resources/mapper/interview/InterviewMapper.xml");
    private static final Path TRAINING_XML = Path.of("src/main/resources/mapper/interview/InterviewTrainingMapper.xml");
    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path PATCH = Path.of(
            "src/main/resources/db/patches/20260712_d_interview_context_soft_delete.sql");

    @Test
    void preparationContextUsesVersionedProfileAndLatestSuccessfulFitAnalysis() throws Exception {
        String xml = Files.readString(XML);
        String context = statement(xml, "select", "findPreparationContext");

        assertThat(context)
                .contains("user_profile_version")
                .contains("latest_profile.deleted_at IS NULL")
                .contains("job_analysis")
                .contains("company_analysis")
                .contains("fit_analysis")
                .contains("latest_fit.status = 'SUCCESS'")
                .contains("fa.missing_skills")
                .contains("fa.strategy_actions")
                .contains("ac.user_id = #{userId}")
                .contains("ac.deleted_at IS NULL");
        assertThat(statement(xml, "update", "updateSessionSourceSnapshot"))
                .contains("source_snapshot = #{sourceSnapshot}")
                .contains("deleted_at IS NULL");
    }

    @Test
    void regeneratedQuestionsAndDeletedSessionTrainingSamplesAreSoftDeleted() throws Exception {
        String xml = Files.readString(XML);
        String training = Files.readString(TRAINING_XML);
        String schema = Files.readString(SCHEMA);
        String patch = Files.readString(PATCH);

        assertThat(statement(xml, "update", "softDeleteQuestionsBySessionId"))
                .contains("SET deleted_at = NOW()")
                .doesNotContain("DELETE FROM");
        assertThat(statement(xml, "select", "findQuestionsBySessionId"))
                .contains("q.deleted_at IS NULL");
        assertThat(statement(xml, "update", "softDeleteTrainingSamplesBySessionId"))
                .contains("SET deleted_at = NOW()");
        assertThat(training).contains("sample.deleted_at IS NULL", "session.deleted_at IS NULL", "ac.deleted_at IS NULL");
        assertThat(schema).contains("source_snapshot     JSON NULL", "idx_interview_question_deleted", "idx_training_deleted");
        assertThat(patch).contains("information_schema.COLUMNS", "information_schema.STATISTICS", "session.deleted_at");
    }

    private static String statement(String xml, String element, String id) {
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).as(id).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).as(id).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
