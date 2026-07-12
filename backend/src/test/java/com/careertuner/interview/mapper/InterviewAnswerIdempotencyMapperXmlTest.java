package com.careertuner.interview.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InterviewAnswerIdempotencyMapperXmlTest {

    private static final Path INTERVIEW_XML =
            Path.of("src/main/resources/mapper/interview/InterviewMapper.xml");
    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path PATCH =
            Path.of("src/main/resources/db/patches/20260712_interview_answer_idempotency.sql");

    @Test
    void reservationUsesQuestionScopedUniqueKeyBeforeCompletion() throws Exception {
        String xml = Files.readString(INTERVIEW_XML);
        String reserve = statement(xml, "insert", "insertAnswerReservation");
        String complete = statement(xml, "update", "completeAnswerReservation");
        String replay = statement(xml, "select", "findAnswerByQuestionIdAndClientSubmissionId");

        assertThat(reserve)
                .contains("timeout=\"3\"")
                .contains("INSERT IGNORE INTO interview_answer")
                .contains("question_id, client_submission_id, submission_status")
                .contains("'PENDING'");
        assertThat(complete)
                .contains("a.submission_status = 'COMPLETED'")
                .contains("a.submission_status = 'PENDING'")
                .contains("s.deleted_at IS NULL")
                .contains("ac.deleted_at IS NULL");
        assertThat(replay)
                .contains("question_id = #{questionId}")
                .contains("client_submission_id = #{clientSubmissionId}");
    }

    @Test
    void pendingReservationsAreExcludedFromUserAndAggregateReads() throws Exception {
        String interview = Files.readString(INTERVIEW_XML);
        assertThat(statement(interview, "select", "findAnswersBySessionId"))
                .contains("a.submission_status = 'COMPLETED'");
        assertThat(statement(interview, "select", "findLatestAnswerByQuestionId"))
                .contains("submission_status = 'COMPLETED'");
        assertThat(statement(interview, "select", "findAnswerByIdAndUserId"))
                .contains("a.submission_status = 'COMPLETED'");
        assertThat(statement(interview, "select", "findSessionsByUserId"))
                .contains("a.submission_status = 'COMPLETED'");

        String admin = Files.readString(
                Path.of("src/main/resources/mapper/admin/interview/AdminInterviewMapper.xml"));
        String dashboard = Files.readString(
                Path.of("src/main/resources/mapper/dashboard/DashboardMapper.xml"));
        String analysis = Files.readString(
                Path.of("src/main/resources/mapper/analysis/AnalysisMapper.xml"));
        assertThat(admin).contains("a.submission_status = 'COMPLETED'");
        assertThat(dashboard).contains("ia.submission_status = 'COMPLETED'");
        assertThat(analysis)
                .contains("answer_count_source.submission_status = 'COMPLETED'")
                .contains("answer_score_source.submission_status = 'COMPLETED'")
                .contains("scored_answer_count_source.submission_status = 'COMPLETED'")
                .contains("corrected_answer_source.submission_status = 'COMPLETED'")
                .contains("ia.submission_status = 'COMPLETED'");
    }

    @Test
    void canonicalSchemaAndRerunnablePatchDefineAndVerifyTheSameContract() throws Exception {
        String schema = Files.readString(SCHEMA);
        String patch = Files.readString(PATCH);

        assertThat(schema)
                .contains("client_submission_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL")
                .contains("submission_status    ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'COMPLETED'")
                .contains("UNIQUE KEY uk_interview_answer_question_submission (question_id, client_submission_id)");
        assertThat(patch)
                .contains("information_schema.COLUMNS")
                .contains("information_schema.STATISTICS")
                .contains("ADD UNIQUE INDEX uk_interview_answer_question_submission")
                .contains("@ct_answer_idempotency_index_exists = 0")
                .contains("DROP TEMPORARY TABLE IF EXISTS ct_answer_idempotency_guard")
                .contains("CHECK (guard_ok = 1)")
                .contains("'PASS', 'FAIL'");
    }

    private static String statement(String xml, String element, String id) {
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).as(id + " 존재").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).as(id + " 종료 태그 존재").isGreaterThan(start);
        return xml.substring(start, end);
    }
}
