package com.careertuner.admin.fitanalysis.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class AdminFitAnalysisR3MapperXmlTest {

    @Test
    void reviewRequiredOnlyFilterKeepsLegacyNullRowsOutOfServerFilteredList() {
        String findAll = selectBlock(mapperXml("mapper/admin/fitanalysis/AdminFitAnalysisMapper.xml"), "findAll");
        String sql = normalize(findAll);

        assertThat(sql).contains("LEFT JOIN fit_analysis_gate_result gr ON gr.fit_analysis_id = fa.id");
        assertThat(sql).contains("<if test=\"reviewRequiredOnly\"> gr.gate_status = 'REVIEW_REQUIRED' </if>");
        assertThat(sql).doesNotContain("OR gr.gate_status IS NULL");
        assertThat(sql).doesNotContain("COALESCE(gr.gate_status");
    }

    @Test
    void adminHomeReviewRequiredCountUsesLatestFitAnalysisPerApplicationCase() {
        assertLatestReviewRequiredCount("mapper/admin/home/AdminHomeMapper.xml");
    }

    @Test
    void adminDashboardReviewRequiredCountUsesLatestFitAnalysisPerApplicationCase() {
        assertLatestReviewRequiredCount("mapper/admin/dashboard/AdminDashboardMapper.xml");
    }

    private static void assertLatestReviewRequiredCount(String resourcePath) {
        String countSql = normalize(selectBlock(mapperXml(resourcePath), "countReviewRequiredAnalyses"));

        assertThat(countSql).contains("INNER JOIN fit_analysis_gate_result gr ON gr.fit_analysis_id = fa.id");
        assertThat(countSql).contains("gr.gate_status = 'REVIEW_REQUIRED'");
        assertThat(countSql).contains("SELECT MAX(latest_fa.id)");
        assertThat(countSql).contains("WHERE latest_fa.application_case_id = fa.application_case_id");
    }

    private static String mapperXml(String resourcePath) {
        try (InputStream input = Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath),
                "Missing mapper XML resource: " + resourcePath)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read mapper XML: " + resourcePath, exception);
        }
    }

    private static String selectBlock(String xml, String id) {
        String start = "<select id=\"" + id + "\"";
        int startIndex = xml.indexOf(start);
        assertThat(startIndex).as("select id=%s exists", id).isGreaterThanOrEqualTo(0);
        int endIndex = xml.indexOf("</select>", startIndex);
        assertThat(endIndex).as("select id=%s has closing tag", id).isGreaterThan(startIndex);
        return xml.substring(startIndex, endIndex);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
