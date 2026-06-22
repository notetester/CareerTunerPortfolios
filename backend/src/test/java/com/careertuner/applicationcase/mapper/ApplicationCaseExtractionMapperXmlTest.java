package com.careertuner.applicationcase.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ApplicationCaseExtractionMapperXmlTest {

    @Test
    void activeExtractionQueryOnlyIncludesQueuedAndRunningStatuses() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/applicationcase/ApplicationCaseExtractionMapper.xml"));
        int selectStart = xml.indexOf("<select id=\"findActiveExtractionsByUserId\"");
        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        String activeQuery = xml.substring(selectStart, xml.indexOf("</select>", selectStart));

        assertThat(activeQuery).contains("status IN ('QUEUED', 'RUNNING')");
        assertThat(activeQuery).doesNotContain("SUCCEEDED", "FAILED");
    }

    @Test
    void claimQueuedExtractionOnlyTransitionsQueuedRowsToRunning() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/applicationcase/ApplicationCaseExtractionMapper.xml"));
        int updateStart = xml.indexOf("<update id=\"claimQueuedExtraction\"");
        assertThat(updateStart).isGreaterThanOrEqualTo(0);
        String claimUpdate = xml.substring(updateStart, xml.indexOf("</update>", updateStart));

        assertThat(claimUpdate).contains("status = 'RUNNING'");
        assertThat(claimUpdate).contains("started_at = NOW()");
        assertThat(claimUpdate).contains("WHERE id = #{id}");
        assertThat(claimUpdate).contains("status = 'QUEUED'");
    }

    @Test
    void staleRunningQueryUsesStartedAtCutoffAndOnlyRunningStatus() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/applicationcase/ApplicationCaseExtractionMapper.xml"));
        int selectStart = xml.indexOf("<select id=\"findStaleRunningExtractions\"");
        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        String staleQuery = xml.substring(selectStart, xml.indexOf("</select>", selectStart));

        assertThat(staleQuery).contains("status = 'RUNNING'");
        assertThat(staleQuery).contains("started_at &lt; #{startedBefore}");
        assertThat(staleQuery).contains("ORDER BY started_at ASC, id ASC");
        assertThat(staleQuery).contains("LIMIT #{limit}");
    }

    @Test
    void runningExtractionGuardLocksOnlyRunningRowBeforeSuccessSideEffects() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/applicationcase/ApplicationCaseExtractionMapper.xml"));
        int selectStart = xml.indexOf("<select id=\"findRunningExtractionForUpdate\"");
        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        String lockQuery = xml.substring(selectStart, xml.indexOf("</select>", selectStart));

        assertThat(lockQuery).contains("WHERE id = #{id}");
        assertThat(lockQuery).contains("status = 'RUNNING'");
        assertThat(lockQuery).contains("FOR UPDATE");
    }

    @Test
    void retryActiveExtractionCountOnlyCountsQueuedAndRunningStatuses() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/applicationcase/ApplicationCaseExtractionMapper.xml"));
        int selectStart = xml.indexOf("<select id=\"countActiveExtractionsByApplicationCaseId\"");
        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        String activeCountQuery = xml.substring(selectStart, xml.indexOf("</select>", selectStart));

        assertThat(activeCountQuery).contains("application_case_id = #{applicationCaseId}");
        assertThat(activeCountQuery).contains("status IN ('QUEUED', 'RUNNING')");
        assertThat(activeCountQuery).doesNotContain("SUCCEEDED", "FAILED");
    }

    @Test
    void latestBulkExtractionQueryScopesToCurrentUserCasesAndRanksLatestRows() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/mapper/applicationcase/ApplicationCaseExtractionMapper.xml"));
        int selectStart = xml.indexOf("<select id=\"findLatestExtractionsByApplicationCaseIdsAndUserId\"");
        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        String latestBulkQuery = xml.substring(selectStart, xml.indexOf("</select>", selectStart));

        assertThat(latestBulkQuery).contains("ROW_NUMBER() OVER");
        assertThat(latestBulkQuery).contains("PARTITION BY e.application_case_id");
        assertThat(latestBulkQuery).contains("ORDER BY e.created_at DESC, e.id DESC");
        assertThat(latestBulkQuery).contains("INNER JOIN application_case ac ON ac.id = e.application_case_id");
        assertThat(latestBulkQuery).contains("ac.user_id = #{userId}");
        assertThat(latestBulkQuery).contains("ac.deleted_at IS NULL");
        assertThat(latestBulkQuery).contains("<foreach collection=\"applicationCaseIds\"");
        assertThat(latestBulkQuery).contains("WHERE rn = 1");
    }

    @Test
    void schemaDefinesUniqueGeneratedColumnForOneActiveExtractionPerCase() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        int tableStart = schema.indexOf("CREATE TABLE IF NOT EXISTS application_case_extraction");
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        String tableDefinition = schema.substring(tableStart, schema.indexOf(") ENGINE", tableStart));

        assertThat(tableDefinition).contains("active_status_marker");
        assertThat(tableDefinition).doesNotContain("active_application_case_id");
        assertThat(tableDefinition).contains("GENERATED ALWAYS AS");
        assertThat(tableDefinition).contains("status IN ('QUEUED', 'RUNNING')");
        assertThat(tableDefinition)
                .contains("UNIQUE KEY uk_case_extraction_active (application_case_id, active_status_marker)");
    }

    @Test
    void schemaAndPatchDefineExtractionQualityGateColumns() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260617_b_application_case_extraction_quality_gate.sql"));
        int tableStart = schema.indexOf("CREATE TABLE IF NOT EXISTS application_case_extraction");
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        String tableDefinition = schema.substring(tableStart, schema.indexOf(") ENGINE", tableStart));

        for (String column : List.of(
                "extraction_strategy",
                "quality_score",
                "quality_status",
                "quality_report_json",
                "model_versions_json",
                "fallback_eligible",
                "fallback_reason",
                "reviewed_at")) {
            assertThat(tableDefinition).contains(column);
            assertThat(patch).contains(column);
        }
        assertThat(tableDefinition).contains("chk_case_extraction_quality_status");
        assertThat(tableDefinition).contains("'PASS', 'REVIEW_REQUIRED', 'FAILED'");
        assertThat(patch).contains("chk_case_extraction_quality_status");
        assertThat(patch).contains("''PASS'', ''REVIEW_REQUIRED'', ''FAILED''");
    }

    @Test
    void schemaAndPatchDefineRuntimeAiSettingTable() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260617_c_ai_runtime_setting.sql"));
        int tableStart = schema.indexOf("CREATE TABLE IF NOT EXISTS ai_runtime_setting");
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        String tableDefinition = schema.substring(tableStart, schema.indexOf(") ENGINE", tableStart));

        for (String column : List.of("setting_key", "value_json", "updated_by", "created_at", "updated_at")) {
            assertThat(tableDefinition).contains(column);
            assertThat(patch).contains(column);
        }
        assertThat(tableDefinition).contains("PRIMARY KEY (setting_key)");
        assertThat(tableDefinition).contains("fk_ai_runtime_setting_updated_by");
        assertThat(patch).contains("PRIMARY KEY (setting_key)");
        assertThat(patch).contains("fk_ai_runtime_setting_updated_by");
    }
}
