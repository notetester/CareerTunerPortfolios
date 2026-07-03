package com.careertuner.admin.fitanalysis.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import com.careertuner.admin.fitanalysis.domain.AdminGateStatsRow;
import com.careertuner.admin.fitanalysis.dto.AdminGateStatsResponse;
import com.careertuner.admin.fitanalysis.service.AdminFitAnalysisServiceImpl;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * 관리자 gate 통계(gate-stats) 집계의 <b>실제 DB fixture 통합 검증</b>(임베디드 H2).
 *
 * <p>{@code AdminFitAnalysisDbFixtureIntegrationTest} 와 같은 fixture 로 전체 gate 행(5건)의 분포 집계를
 * 검증한다: gate/review/severity 분포(건수 내림차순, NULL 컬럼 제외), gate_reasons_json 의
 * null/빈배열/정상/깨진 JSON 안전 처리(깨진 JSON 은 brokenReasonsJsonCount 로만 집계), top claim 순위.
 *
 * <p>fixture 시나리오는 {@code db/testfixture/admin_fitanalysis_data.sql} 주석 참조.
 */
@MybatisTest
@Sql({"/db/testfixture/admin_fitanalysis_schema.sql", "/db/testfixture/admin_fitanalysis_data.sql"})
class AdminFitAnalysisGateStatsDbFixtureIntegrationTest {

    @Autowired
    private AdminFitAnalysisMapper adminFitAnalysisMapper;
    @Autowired
    private FitAnalysisMapper fitAnalysisMapper;

    private AdminFitAnalysisServiceImpl service() {
        return new AdminFitAnalysisServiceImpl(adminFitAnalysisMapper, fitAnalysisMapper, new ObjectMapper());
    }

    @Test
    void findAllGateRowsReturnsEveryGateRowIncludingBrokenJsonRow() {
        List<AdminGateStatsRow> rows = adminFitAnalysisMapper.findAllGateRows();

        assertThat(rows).hasSize(5);
        assertThat(rows).extracting(AdminGateStatsRow::getGateStatus)
                .containsOnly("REVIEW_REQUIRED", "PASSED");
    }

    @Test
    void gateStatsAggregatesStatusDistributionsInCountDescOrder() {
        AdminGateStatsResponse stats = service().getGateStats();

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.byGateStatus()).containsExactly(
                entry("REVIEW_REQUIRED", 4L),
                entry("PASSED", 1L));
        assertThat(stats.byReviewStatus()).containsExactly(
                entry("PENDING", 4L),
                entry("RESOLVED", 1L));
        // f6(PASSED) 의 max_severity=NULL 은 분포에서 제외한다("null" 키 없음).
        assertThat(stats.byMaxSeverity()).containsExactly(
                entry("warning", 3L),
                entry("critical", 1L));
    }

    @Test
    void gateStatsAggregatesReasonDistributionsAndCountsBrokenJsonSafely() {
        AdminGateStatsResponse stats = service().getGateStats();

        // reasons 4건 = f2(Kafka/warning) + f3(Kafka/critical, Redis/warning) + f4(Spark/warning). f6 '[]' 는 0건.
        assertThat(stats.byReasonType()).containsExactly(
                entry("requirement_as_owned", 3L),
                entry("matched_skill_without_user_evidence", 1L));
        assertThat(stats.byReasonSeverity()).containsExactly(
                entry("warning", 3L),
                entry("critical", 1L));
        // f7 의 깨진 JSON 은 집계를 중단하지 않고 broken 건수로만 센다.
        assertThat(stats.brokenReasonsJsonCount()).isEqualTo(1);
    }

    @Test
    void gateStatsTopClaimsOrderedByCountDescThenAlphabetical() {
        AdminGateStatsResponse stats = service().getGateStats();

        assertThat(stats.topClaims()).containsExactly(
                new AdminGateStatsResponse.TopClaim("Kafka", 2L),
                new AdminGateStatsResponse.TopClaim("Redis", 1L),
                new AdminGateStatsResponse.TopClaim("Spark", 1L));
    }
}
