package com.careertuner.admin.fitanalysis.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import com.careertuner.admin.dashboard.mapper.AdminDashboardMapper;
import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.service.AdminFitAnalysisServiceImpl;
import com.careertuner.admin.home.mapper.AdminHomeMapper;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * 관리자 fit-analysis SQL 의 <b>실제 DB fixture 통합 검증</b>(임베디드 H2).
 *
 * <p>기존 {@code AdminFitAnalysisR3MapperXmlTest} 는 XML 문자열 정적 검사였다 — 여기서는 실제 스키마·데이터에
 * SQL 을 실행해 검증한다(model-card known limitation 해소): legacy gate=null 행 안전, 혼합 gate 상태 목록,
 * gateReasonsJson null/빈배열/정상/깨진 JSON 안전 처리, reviewRequiredOnly 서버 필터, home/dashboard 검토
 * 대기 count 의 최신-지원건 기준 일치, gate review workflow 갱신.
 *
 * <p>fixture 시나리오는 {@code db/testfixture/admin_fitanalysis_data.sql} 주석 참조(검토대기 기대값 2 = f3+f7).
 */
@MybatisTest
@Sql({"/db/testfixture/admin_fitanalysis_schema.sql", "/db/testfixture/admin_fitanalysis_data.sql"})
class AdminFitAnalysisDbFixtureIntegrationTest {

    @Autowired
    private AdminFitAnalysisMapper adminFitAnalysisMapper;
    @Autowired
    private AdminHomeMapper adminHomeMapper;
    @Autowired
    private AdminDashboardMapper adminDashboardMapper;
    @Autowired
    private FitAnalysisMapper fitAnalysisMapper;

    private AdminFitAnalysisServiceImpl service() {
        return new AdminFitAnalysisServiceImpl(adminFitAnalysisMapper, fitAnalysisMapper, new ObjectMapper());
    }

    @Test
    void findAllListsMixedGateStatusesIncludingLegacyNullRow() {
        List<AdminFitAnalysisResult> all = adminFitAnalysisMapper.findAll(false);

        assertThat(all).hasSize(6);
        AdminFitAnalysisResult legacy = byId(all, 1L);
        assertThat(legacy.getGateStatus()).isNull();          // R3 이전 행이 깨지지 않는다
        assertThat(legacy.getGateReviewStatus()).isNull();
        assertThat(byId(all, 3L).getGateStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(byId(all, 6L).getGateStatus()).isEqualTo("PASSED");
        assertThat(byId(all, 3L).isReanalysisRequested()).isTrue();   // REANALYSIS 메모 EXISTS
        assertThat(byId(all, 3L).getMemoCount()).isEqualTo(2);
    }

    @Test
    void reviewRequiredOnlyServerFilterReturnsOnlyReviewRequiredRows() {
        List<AdminFitAnalysisResult> filtered = adminFitAnalysisMapper.findAll(true);

        assertThat(filtered).extracting(AdminFitAnalysisResult::getId).containsExactlyInAnyOrder(2L, 3L, 4L, 7L);
        assertThat(filtered).allSatisfy(row -> assertThat(row.getGateStatus()).isEqualTo("REVIEW_REQUIRED"));
    }

    @Test
    void gateReasonsJsonVariantsAreHandledSafelyThroughService() {
        AdminFitAnalysisServiceImpl service = service();

        AdminFitAnalysisDetailResponse normal = service.get(3L);
        assertThat(normal.gateReasons()).hasSize(2);
        assertThat(normal.gateReasons().get(0).claim()).isEqualTo("Kafka");
        assertThat(normal.gateMaxSeverity()).isEqualTo("critical");

        assertThat(service.get(6L).gateReasons()).isEmpty();          // '[]'
        assertThat(service.get(7L).gateReasons()).isEmpty();          // 깨진 JSON → 빈 목록(안전)
        assertThat(service.get(7L).gateStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(service.get(1L).gateStatus()).isNull();            // legacy null 그대로 노출
        assertThat(service.get(1L).gateReasons()).isEmpty();
    }

    @Test
    void homeAndDashboardReviewPendingCountsAgreeOnLatestPerCaseBasis() {
        // 최신(MAX id)+PENDING: c102→f3 ✓, c103→최신 f6=PASSED ✗(구 f4 는 최신 아님), c101→gate 없음 ✗, c104→f7 ✓
        assertThat(adminHomeMapper.countReviewRequiredAnalyses()).isEqualTo(2);
        assertThat(adminDashboardMapper.countReviewRequiredAnalyses()).isEqualTo(2);
    }

    @Test
    void gateReviewUpdateChangesStatusReviewerAndDropsFromPendingCount() {
        int updated = adminFitAnalysisMapper.updateGateReview(3L, 9101L, "RESOLVED");
        assertThat(updated).isEqualTo(1);

        AdminFitAnalysisResult row = adminFitAnalysisMapper.findById(3L);
        assertThat(row.getGateReviewStatus()).isEqualTo("RESOLVED");
        assertThat(row.getGateReviewerName()).isEqualTo("한검토");
        assertThat(row.getGateReviewedAt()).isNotNull();

        assertThat(adminHomeMapper.countReviewRequiredAnalyses()).isEqualTo(1);      // f7 만 남음
        assertThat(adminDashboardMapper.countReviewRequiredAnalyses()).isEqualTo(1);
    }

    @Test
    void gateReviewUpdateOnLegacyAnalysisWithoutGateRowAffectsNothing() {
        assertThat(adminFitAnalysisMapper.updateGateReview(1L, 9101L, "RESOLVED")).isZero();
    }

    private static AdminFitAnalysisResult byId(List<AdminFitAnalysisResult> rows, long id) {
        return rows.stream().filter(row -> row.getId().equals(id)).findFirst().orElseThrow();
    }
}
