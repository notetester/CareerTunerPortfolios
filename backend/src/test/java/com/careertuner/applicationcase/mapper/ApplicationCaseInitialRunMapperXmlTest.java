package com.careertuner.applicationcase.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 초기 실행 프로필 mapper 의 claim/fencing/stale SQL 계약을 XML 문자열로 검증한다(기존 mapper 테스트 컨벤션). */
class ApplicationCaseInitialRunMapperXmlTest {

    private static final Path XML = Path.of(
            "src/main/resources/mapper/applicationcase/ApplicationCaseInitialRunMapper.xml");

    private String statement(String openTag) throws Exception {
        String xml = Files.readString(XML);
        int start = xml.indexOf(openTag);
        assertThat(start).isGreaterThanOrEqualTo(0);
        String closeTag = openTag.startsWith("<update") ? "</update>"
                : openTag.startsWith("<insert") ? "</insert>" : "</select>";
        return xml.substring(start, xml.indexOf(closeTag, start));
    }

    @Test
    void claimForRunOnlyTransitionsPendingToRunningWithToken() throws Exception {
        String claim = statement("<update id=\"claimForRun\"");
        assertThat(claim).contains("state = 'RUNNING'");
        assertThat(claim).contains("execution_token = #{executionToken}");
        assertThat(claim).contains("started_at = NOW()");
        assertThat(claim).contains("application_case_id = #{applicationCaseId}");
        assertThat(claim).contains("state = 'PENDING'"); // PENDING 만 claim
    }

    @Test
    void markDoneFencesOnRunningStateAndToken() throws Exception {
        String done = statement("<update id=\"markDone\"");
        assertThat(done).contains("state = 'DONE'");
        assertThat(done).contains("state = 'RUNNING'");
        assertThat(done).contains("execution_token = #{executionToken}"); // fencing
    }

    @Test
    void markFailedFencesOnRunningStateAndTokenAndStoresReason() throws Exception {
        String failed = statement("<update id=\"markFailed\"");
        assertThat(failed).contains("state = 'FAILED'");
        assertThat(failed).contains("state = 'RUNNING'");
        assertThat(failed).contains("execution_token = #{executionToken}"); // fencing
        assertThat(failed).contains("failure_reason = #{failureReason}");
    }

    @Test
    void staleRunningQueryUsesStartedAtCutoffAndOnlyRunning() throws Exception {
        String stale = statement("<select id=\"findStaleRunning\"");
        assertThat(stale).contains("state = 'RUNNING'");
        assertThat(stale).contains("started_at &lt; DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE)");
        assertThat(stale).contains("ORDER BY started_at ASC");
        assertThat(stale).contains("LIMIT #{limit}");
    }

    @Test
    void insertPendingSeedsPendingState() throws Exception {
        String insert = statement("<insert id=\"insertPending\"");
        assertThat(insert).contains("INSERT INTO application_case_initial_run");
        assertThat(insert).contains("'PENDING'");
        assertThat(insert).contains("job_analysis_provider");
        assertThat(insert).contains("company_analysis_provider");
    }

    @Test
    void schemaAndPatchDefineInitialRunTable() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String patch = Files.readString(Path.of(
                "src/main/resources/db/patches/20260710_b_model_selection_run_profile.sql"));
        int tableStart = schema.indexOf("CREATE TABLE IF NOT EXISTS application_case_initial_run");
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        String tableDefinition = schema.substring(tableStart, schema.indexOf(") ENGINE", tableStart));

        for (String column : List.of(
                "application_case_id", "state", "job_analysis_provider",
                "company_analysis_provider", "execution_token", "started_at")) {
            assertThat(tableDefinition).contains(column);
        }
        assertThat(tableDefinition).contains("idx_initial_run_stale (state, started_at)");
        assertThat(tableDefinition).contains("chk_initial_run_state");
        assertThat(patch).contains("CREATE TABLE IF NOT EXISTS application_case_initial_run");
        assertThat(patch).contains("idx_initial_run_stale");
    }
}
