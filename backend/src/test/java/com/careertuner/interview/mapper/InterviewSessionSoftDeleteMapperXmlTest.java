package com.careertuner.interview.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InterviewSessionSoftDeleteMapperXmlTest {

    private static final String ANALYSIS_XML =
            "src/main/resources/mapper/analysis/AnalysisMapper.xml";
    private static final String DASHBOARD_XML =
            "src/main/resources/mapper/dashboard/DashboardMapper.xml";
    private static final String INTERVIEW_XML =
            "src/main/resources/mapper/interview/InterviewMapper.xml";

    @Test
    void analysisQueriesExcludeDeletedSessionsAndInactiveApplicationCases() throws Exception {
        String weekly = statement(ANALYSIS_XML, "select", "findWeeklyMetricsByUserId");
        assertThat(occurrences(weekly, "session.deleted_at IS NULL")).isEqualTo(2);
        assertThat(occurrences(weekly, "ac.archived_at IS NULL")).isEqualTo(6);
        assertThat(occurrences(weekly, "ac.deleted_at IS NULL")).isEqualTo(6);

        String answers = statement(ANALYSIS_XML, "select", "findAnswerSourcesByUserId");
        assertThat(answers).contains("s.deleted_at IS NULL");
    }

    @Test
    void dashboardWeeklyMetricsExcludeDeletedSessionsAndInactiveApplicationCases() throws Exception {
        String weekly = statement(DASHBOARD_XML, "select", "findWeeklyMetricsByUserId");
        assertThat(occurrences(weekly, "session.deleted_at IS NULL")).isEqualTo(2);
        assertThat(occurrences(weekly, "ac.archived_at IS NULL")).isEqualTo(6);
        assertThat(occurrences(weekly, "ac.deleted_at IS NULL")).isEqualTo(6);
    }

    @Test
    void interviewQueriesDoNotReadOrUpdateDeletedSessions() throws Exception {
        assertThat(statement(INTERVIEW_XML, "select", "findLatestScoredSessionScore"))
                .contains("deleted_at IS NULL");
        assertThat(statement(INTERVIEW_XML, "select", "findQuestionByIdAndUserId"))
                .contains("s.deleted_at IS NULL");
        assertThat(statement(INTERVIEW_XML, "update", "updateSessionResult"))
                .contains("deleted_at IS NULL");
        assertThat(statement(INTERVIEW_XML, "update", "softDeleteSession"))
                .contains("ac.deleted_at IS NULL", "s.deleted_at IS NULL");
        assertThat(statement(INTERVIEW_XML, "update", "touchSessionResumed"))
                .contains("ac.deleted_at IS NULL", "s.deleted_at IS NULL");
    }

    private static String statement(String file, String element, String id) throws Exception {
        String xml = Files.readString(Path.of(file));
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).as(id + " 존재").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).as(id + " 종료 태그 존재").isGreaterThan(start);
        return xml.substring(start, end);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
