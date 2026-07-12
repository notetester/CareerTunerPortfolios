package com.careertuner.admin.interview.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminInterviewMapperXmlTest {

    private static final String MAPPER_XML =
            "src/main/resources/mapper/admin/interview/AdminInterviewMapper.xml";

    @Test
    void sessionQueriesExcludeSoftDeletedSessions() throws Exception {
        assertThat(statement("select", "findSessions")).contains("WHERE s.deleted_at IS NULL");
        assertThat(statement("select", "countSessions")).contains("WHERE s.deleted_at IS NULL");
        assertThat(statement("select", "findSession")).contains("s.deleted_at IS NULL");
        assertThat(statement("select", "findReport")).contains("deleted_at IS NULL");
        assertThat(statement("select", "findAdminMemo")).contains("deleted_at IS NULL");
        assertThat(statement("select", "findAdminMemoForUpdate"))
                .contains("deleted_at IS NULL")
                .contains("FOR UPDATE");
        assertThat(statement("update", "updateAdminMemo")).contains("deleted_at IS NULL");
    }

    @Test
    void reportFilterHandlesBothTrueAndFalse() throws Exception {
        String sessions = statement("select", "findSessions");
        String count = statement("select", "countSessions");

        assertThat(sessions)
                .contains("<when test=\"hasReport\">")
                .contains("AND s.report IS NOT NULL AND s.report &lt;&gt; ''")
                .contains("AND (s.report IS NULL OR s.report = '')");
        assertThat(count)
                .contains("<when test=\"hasReport\">")
                .contains("AND s.report IS NOT NULL AND s.report &lt;&gt; ''")
                .contains("AND (s.report IS NULL OR s.report = '')");
    }

    @Test
    void summaryOnlyAggregatesActiveSessions() throws Exception {
        String summary = statement("select", "findSummary");

        assertThat(summary)
                .contains("FROM interview_session active")
                .contains("active.total_score IS NOT NULL AND active.deleted_at IS NULL")
                .contains("active_case.deleted_at IS NULL")
                .contains("WHERE active_session.deleted_at IS NULL");
    }

    private static String statement(String element, String id) throws Exception {
        String xml = Files.readString(Path.of(MAPPER_XML));
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).as(id + " 존재").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).as(id + " 종료 태그 존재").isGreaterThan(start);
        return xml.substring(start, end);
    }
}
