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
        String xml = mapperXml("mapper/admin/fitanalysis/AdminFitAnalysisMapper.xml");
        String findAll = normalize(selectBlock(xml, "findAll"));
        // 서버측 필터는 findAll/countAll 이 공유하는 <sql id="AdminFitAnalysisFilters"> 조각으로 이동했다.
        String filters = normalize(sqlBlock(xml, "AdminFitAnalysisFilters"));

        assertThat(findAll).contains("LEFT JOIN fit_analysis_gate_result gr ON gr.fit_analysis_id = fa.id");
        assertThat(findAll).contains("<include refid=\"AdminFitAnalysisFilters\"/>");
        // reviewRequiredOnly 는 REVIEW_REQUIRED 만 남긴다(legacy gate_status=null 행은 포함하지 않는다).
        assertThat(filters).contains("<if test=\"c.reviewRequiredOnly\"> AND gr.gate_status = 'REVIEW_REQUIRED' </if>");
        assertThat(filters).doesNotContain("OR gr.gate_status IS NULL");
        assertThat(filters).doesNotContain("COALESCE(gr.gate_status");
    }

    @Test
    void findAllAndCountAllSharePagingAndFilterContract() {
        String xml = mapperXml("mapper/admin/fitanalysis/AdminFitAnalysisMapper.xml");
        String findAll = normalize(selectBlock(xml, "findAll"));
        String countAll = normalize(selectBlock(xml, "countAll"));

        // 목록은 LIMIT/OFFSET 로 페이지 단위만 읽고, 건수와 목록은 동일 필터 조각을 공유한다.
        assertThat(findAll).contains("LIMIT #{c.size} OFFSET #{c.offset}");
        assertThat(findAll).contains("ORDER BY fa.created_at DESC, fa.id DESC");
        assertThat(countAll).contains("SELECT COUNT(*)");
        assertThat(countAll).contains("<include refid=\"AdminFitAnalysisFilters\"/>");
        assertThat(countAll).doesNotContain("LIMIT");
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

    private static String sqlBlock(String xml, String id) {
        String start = "<sql id=\"" + id + "\"";
        int startIndex = xml.indexOf(start);
        assertThat(startIndex).as("sql id=%s exists", id).isGreaterThanOrEqualTo(0);
        int endIndex = xml.indexOf("</sql>", startIndex);
        assertThat(endIndex).as("sql id=%s has closing tag", id).isGreaterThan(startIndex);
        return xml.substring(startIndex, endIndex);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
