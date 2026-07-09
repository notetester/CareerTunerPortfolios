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
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListCriteria;
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

    /** reviewRequiredOnly 만 지정하고 나머지 필터 없음, 한 페이지에 전건이 들어가는 크기(100). */
    private static AdminFitAnalysisListCriteria criteria(boolean reviewRequiredOnly) {
        return new AdminFitAnalysisListCriteria(reviewRequiredOnly, null, "ALL", "ALL", false, false, 100, 0);
    }

    @Test
    void findAllListsMixedGateStatusesIncludingLegacyNullRow() {
        List<AdminFitAnalysisResult> all = adminFitAnalysisMapper.findAll(criteria(false));

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
        List<AdminFitAnalysisResult> filtered = adminFitAnalysisMapper.findAll(criteria(true));

        assertThat(filtered).extracting(AdminFitAnalysisResult::getId).containsExactlyInAnyOrder(2L, 3L, 4L, 7L);
        assertThat(filtered).allSatisfy(row -> assertThat(row.getGateStatus()).isEqualTo("REVIEW_REQUIRED"));
        assertThat(adminFitAnalysisMapper.countAll(criteria(true))).isEqualTo(4L);
    }

    @Test
    void countAllMatchesFindAllForUnfilteredQuery() {
        assertThat(adminFitAnalysisMapper.countAll(criteria(false))).isEqualTo(6L);
    }

    @Test
    void paginationAppliesLimitOffsetInLatestFirstOrder() {
        // created_at DESC, id DESC 순: [7, 6, 3, 4, 2, 1]. size=2 로 세 페이지에 걸쳐 확인한다.
        AdminFitAnalysisListCriteria p1 = new AdminFitAnalysisListCriteria(false, null, "ALL", "ALL", false, false, 2, 0);
        AdminFitAnalysisListCriteria p2 = new AdminFitAnalysisListCriteria(false, null, "ALL", "ALL", false, false, 2, 2);
        AdminFitAnalysisListCriteria p3 = new AdminFitAnalysisListCriteria(false, null, "ALL", "ALL", false, false, 2, 4);

        assertThat(adminFitAnalysisMapper.findAll(p1)).extracting(AdminFitAnalysisResult::getId).containsExactly(7L, 6L);
        assertThat(adminFitAnalysisMapper.findAll(p2)).extracting(AdminFitAnalysisResult::getId).containsExactly(3L, 4L);
        assertThat(adminFitAnalysisMapper.findAll(p3)).extracting(AdminFitAnalysisResult::getId).containsExactly(2L, 1L);
        // 필터가 없으므로 페이지가 나뉘어도 total 은 6 으로 동일하다.
        assertThat(adminFitAnalysisMapper.countAll(p1)).isEqualTo(6L);
    }

    @Test
    void bandFilterSelectsScoreRange() {
        // MID(50~70): f2(55), f3(61), f6(66)
        AdminFitAnalysisListCriteria mid = new AdminFitAnalysisListCriteria(false, null, "MID", "ALL", false, false, 100, 0);
        assertThat(adminFitAnalysisMapper.findAll(mid)).extracting(AdminFitAnalysisResult::getId)
                .containsExactlyInAnyOrder(2L, 3L, 6L);
        assertThat(adminFitAnalysisMapper.countAll(mid)).isEqualTo(3L);
        // LOW(<50): f4(40), f7(48)
        AdminFitAnalysisListCriteria low = new AdminFitAnalysisListCriteria(false, null, "LOW", "ALL", false, false, 100, 0);
        assertThat(adminFitAnalysisMapper.findAll(low)).extracting(AdminFitAnalysisResult::getId)
                .containsExactlyInAnyOrder(4L, 7L);
    }

    @Test
    void queryFilterMatchesCompanyJobUserNameOrEmail() {
        // 회사명 부분일치
        AdminFitAnalysisListCriteria byCompany = new AdminFitAnalysisListCriteria(false, "네이버", "ALL", "ALL", false, false, 100, 0);
        assertThat(adminFitAnalysisMapper.findAll(byCompany)).extracting(AdminFitAnalysisResult::getId)
                .containsExactlyInAnyOrder(2L, 3L);
        // 직무명 부분일치("개발자" 는 카카오/네이버/라인 3개 케이스)
        AdminFitAnalysisListCriteria byJob = new AdminFitAnalysisListCriteria(false, "개발자", "ALL", "ALL", false, false, 100, 0);
        assertThat(adminFitAnalysisMapper.findAll(byJob)).extracting(AdminFitAnalysisResult::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 7L);
    }

    @Test
    void memoAndReanalysisFiltersSelectOnlyRowsWithMemos() {
        // fixture 상 메모/재분석 메모가 있는 유일한 행은 f3.
        AdminFitAnalysisListCriteria memoOnly = new AdminFitAnalysisListCriteria(false, null, "ALL", "ALL", true, false, 100, 0);
        assertThat(adminFitAnalysisMapper.findAll(memoOnly)).extracting(AdminFitAnalysisResult::getId).containsExactly(3L);

        AdminFitAnalysisListCriteria reanalysisOnly = new AdminFitAnalysisListCriteria(false, null, "ALL", "ALL", false, true, 100, 0);
        assertThat(adminFitAnalysisMapper.findAll(reanalysisOnly)).extracting(AdminFitAnalysisResult::getId).containsExactly(3L);
    }

    @Test
    void resultFilterSplitsSuccessAndFail() {
        // fixture 는 전부 status='SUCCESS' → SUCCESS 는 6건, FAIL 은 0건.
        AdminFitAnalysisListCriteria success = new AdminFitAnalysisListCriteria(false, null, "ALL", "SUCCESS", false, false, 100, 0);
        assertThat(adminFitAnalysisMapper.countAll(success)).isEqualTo(6L);
        AdminFitAnalysisListCriteria fail = new AdminFitAnalysisListCriteria(false, null, "ALL", "FAIL", false, false, 100, 0);
        assertThat(adminFitAnalysisMapper.findAll(fail)).isEmpty();
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
