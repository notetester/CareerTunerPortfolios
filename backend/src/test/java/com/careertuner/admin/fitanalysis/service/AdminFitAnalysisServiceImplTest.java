package com.careertuner.admin.fitanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.fitanalysis.domain.AdminFitAnalysisResult;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisDetailResponse;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListCriteria;
import com.careertuner.admin.fitanalysis.dto.AdminFitAnalysisListQuery;
import com.careertuner.admin.fitanalysis.mapper.AdminFitAnalysisMapper;
import com.careertuner.fitanalysis.mapper.FitAnalysisMapper;

import tools.jackson.databind.ObjectMapper;

class AdminFitAnalysisServiceImplTest {

    /** 필터 없는 기본 조회 쿼리(page=1, size=20). */
    private static AdminFitAnalysisListQuery query() {
        return new AdminFitAnalysisListQuery(false, null, "ALL", "ALL", false, false, 1, 20);
    }

    @Test
    void listHandlesMixedGateStatusesAndLegacyNulls() {
        AdminFitAnalysisMapper adminMapper = mock(AdminFitAnalysisMapper.class);
        FitAnalysisMapper fitAnalysisMapper = mock(FitAnalysisMapper.class);
        AdminFitAnalysisServiceImpl service = new AdminFitAnalysisServiceImpl(adminMapper, fitAnalysisMapper, new ObjectMapper());
        when(adminMapper.countAll(any())).thenReturn(4L);
        when(adminMapper.findAll(any())).thenReturn(List.of(
                result(1L, "PASSED", false, 0, null, null),
                result(2L, "REVIEW_REQUIRED", true, 2, "critical", null),
                result(3L, "REJECTED", true, 1, "critical", null),
                result(4L, null, null, null, null, null)));

        var page = service.list(query());
        var responses = page.items();

        assertThat(page.total()).isEqualTo(4L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(responses).hasSize(4);
        assertThat(responses).extracting("gateStatus")
                .containsExactly("PASSED", "REVIEW_REQUIRED", "REJECTED", null);
        assertThat(responses).extracting("needsHumanReview")
                .containsExactly(false, true, true, false);
        assertThat(responses).extracting("gateReasonCount")
                .containsExactly(0, 2, 1, 0);
        assertThat(responses.get(3).matchedSkills()).containsExactly("React");
        assertThat(responses.get(3).missingSkills()).containsExactly("AWS");
    }

    @Test
    void listPassesReviewRequiredOnlyFlagToMapper() {
        AdminFitAnalysisMapper adminMapper = mock(AdminFitAnalysisMapper.class);
        FitAnalysisMapper fitAnalysisMapper = mock(FitAnalysisMapper.class);
        AdminFitAnalysisServiceImpl service = new AdminFitAnalysisServiceImpl(adminMapper, fitAnalysisMapper, new ObjectMapper());
        when(adminMapper.countAll(any())).thenReturn(1L);
        when(adminMapper.findAll(any())).thenReturn(List.of(
                result(2L, "REVIEW_REQUIRED", true, 1, "warning", null)));

        var page = service.list(new AdminFitAnalysisListQuery(true, null, "ALL", "ALL", false, false, 1, 20));

        ArgumentCaptor<AdminFitAnalysisListCriteria> captor = ArgumentCaptor.forClass(AdminFitAnalysisListCriteria.class);
        verify(adminMapper).findAll(captor.capture());
        assertThat(captor.getValue().reviewRequiredOnly()).isTrue();
        assertThat(page.items()).singleElement().satisfies(response -> {
            assertThat(response.gateStatus()).isEqualTo("REVIEW_REQUIRED");
            assertThat(response.needsHumanReview()).isTrue();
        });
    }

    @Test
    void listNormalizesPageAndSizeAndComputesOffset() {
        AdminFitAnalysisMapper adminMapper = mock(AdminFitAnalysisMapper.class);
        FitAnalysisMapper fitAnalysisMapper = mock(FitAnalysisMapper.class);
        AdminFitAnalysisServiceImpl service = new AdminFitAnalysisServiceImpl(adminMapper, fitAnalysisMapper, new ObjectMapper());
        when(adminMapper.countAll(any())).thenReturn(0L);
        when(adminMapper.findAll(any())).thenReturn(List.of());

        // page=0(→1), size=500(→100 상한): offset=0, size=100 으로 정규화된다.
        service.list(new AdminFitAnalysisListQuery(false, "  코딩  ", "high", "success", false, false, 0, 500));

        ArgumentCaptor<AdminFitAnalysisListCriteria> captor = ArgumentCaptor.forClass(AdminFitAnalysisListCriteria.class);
        verify(adminMapper).findAll(captor.capture());
        AdminFitAnalysisListCriteria criteria = captor.getValue();
        assertThat(criteria.size()).isEqualTo(100);
        assertThat(criteria.offset()).isZero();
        assertThat(criteria.query()).isEqualTo("코딩");        // 트림
        assertThat(criteria.scoreBand()).isEqualTo("HIGH");   // 대문자 정규화
        assertThat(criteria.result()).isEqualTo("SUCCESS");
    }

    @Test
    void getPreservesLegacyNullGateStatus() {
        AdminFitAnalysisDetailResponse response = detailFor(result(1L, null, null, null, null, null));

        assertThat(response.gateStatus()).isNull();
        assertThat(response.needsHumanReview()).isFalse();
        assertThat(response.gateReasonCount()).isZero();
        assertThat(response.gateMaxSeverity()).isNull();
        assertThat(response.gateReasons()).isEmpty();
    }

    @Test
    void getTreatsNullGateReasonsJsonAsEmpty() {
        assertThat(detailFor(result(1L, "PASSED", false, 0, null, null)).gateReasons()).isEmpty();
    }

    @Test
    void getTreatsEmptyGateReasonsJsonArrayAsEmpty() {
        assertThat(detailFor(result(1L, "PASSED", false, 0, null, "[]")).gateReasons()).isEmpty();
    }

    @Test
    void getParsesValidGateReasonsJson() {
        AdminFitAnalysisDetailResponse response = detailFor(result(
                1L,
                "REVIEW_REQUIRED",
                true,
                1,
                "critical",
                """
                [{"type":"requirement_as_owned","claim":"Spark","reason":"사용자 원본 근거에 없음","severity":"critical"}]
                """));

        assertThat(response.gateReasons()).singleElement().satisfies(reason -> {
            assertThat(reason.type()).isEqualTo("requirement_as_owned");
            assertThat(reason.claim()).isEqualTo("Spark");
            assertThat(reason.reason()).isEqualTo("사용자 원본 근거에 없음");
            assertThat(reason.severity()).isEqualTo("critical");
        });
    }

    @Test
    void getTreatsBrokenGateReasonsJsonAsEmpty() {
        assertThat(detailFor(result(1L, "REVIEW_REQUIRED", true, 1, "warning", "{broken")).gateReasons()).isEmpty();
    }

    private static AdminFitAnalysisDetailResponse detailFor(AdminFitAnalysisResult result) {
        AdminFitAnalysisMapper adminMapper = mock(AdminFitAnalysisMapper.class);
        FitAnalysisMapper fitAnalysisMapper = mock(FitAnalysisMapper.class);
        when(adminMapper.findById(result.getId())).thenReturn(result);
        when(adminMapper.findMemosByFitAnalysisId(result.getId())).thenReturn(List.of());
        when(fitAnalysisMapper.findLearningTasksByFitAnalysisId(result.getId())).thenReturn(List.of());

        return new AdminFitAnalysisServiceImpl(adminMapper, fitAnalysisMapper, new ObjectMapper()).get(result.getId());
    }

    private static AdminFitAnalysisResult result(Long id,
                                                 String gateStatus,
                                                 Boolean needsHumanReview,
                                                 Integer reasonCount,
                                                 String maxSeverity,
                                                 String gateReasonsJson) {
        return AdminFitAnalysisResult.builder()
                .id(id)
                .applicationCaseId(100L + id)
                .userId(200L + id)
                .userName("테스트 사용자")
                .userEmail("user" + id + "@example.com")
                .companyName("테스트기업")
                .jobTitle("백엔드 개발자")
                .applicationStatus("READY")
                .favorite(false)
                .fitScore(72)
                .matchedSkills("[\"React\"]")
                .missingSkills("[\"AWS\"]")
                .recommendedStudy("[]")
                .recommendedCertificates("[]")
                .strategy("전략")
                .sourceSnapshot("{}")
                .scoreBasis("[]")
                .gapRecommendations("[]")
                .certificateRecommendations("[]")
                .strategyActions("[]")
                .conditionMatrix("[]")
                .analysisConfidence("MEDIUM")
                .applyDecision("HOLD")
                .model("mock")
                .promptVersion("test")
                .status("SUCCESS")
                .createdAt(LocalDateTime.of(2026, 6, 30, 10, 0))
                .memoCount(0)
                .reanalysisRequested(false)
                .gateStatus(gateStatus)
                .gateNeedsHumanReview(needsHumanReview)
                .gateReasonCount(reasonCount)
                .gateMaxSeverity(maxSeverity)
                .evidenceGateVersion(gateStatus == null ? null : "r3-review-first")
                .gateReasonsJson(gateReasonsJson)
                .build();
    }
}
