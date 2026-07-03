package com.careertuner.companyanalysis.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 회사 식별·쿼리 생성·동명 판별 '중간' 강도(235 §11) 단위 테스트.
 * 명백히 불일치할 때만 제외하고, 부분 일치·판별 불가는 유지(과도 제거 방지)를 검증한다.
 */
class CompanySourceResolverTest {

    private final CompanySourceResolver resolver = new CompanySourceResolver();

    private static CompanyWebSearchResult result(String title, String description) {
        return new CompanyWebSearchResult(
                NaverSearchCategory.NEWS, title, "https://news.example.com/1", description, Instant.now());
    }

    // ── 검색 쿼리 생성 ──

    @Test
    void queriesCombineNameWithIndustryAndRegionHints() {
        List<String> queries = resolver.buildQueries(new CompanyIdentity("가온테크", "IT 서비스", "서울"));

        assertThat(queries).containsExactly(
                "가온테크 IT 서비스 서울",
                "가온테크 IT 서비스",
                "가온테크 서울",
                "가온테크");
    }

    @Test
    void queriesFallBackToNameOnlyWhenHintsMissing() {
        assertThat(resolver.buildQueries(new CompanyIdentity("가온테크", "", null)))
                .containsExactly("가온테크");
    }

    @Test
    void queriesSkipBlankHintSlots() {
        assertThat(resolver.buildQueries(new CompanyIdentity("가온테크", "  ", "부산")))
                .containsExactly("가온테크 부산", "가온테크");
    }

    @Test
    void queriesEmptyWhenCompanyNameMissing() {
        assertThat(resolver.buildQueries(new CompanyIdentity("  ", "IT", "서울"))).isEmpty();
    }

    // ── 동명 판별: 명백한 불일치만 제외 ──

    /** 명백한 불일치 = 제목이 법인 표기로 다른 회사를 명시 + 대상 회사명 전무 → 제외. */
    @Test
    void titleNamingDifferentCorporateEntityIsExcluded() {
        List<CompanyWebSearchResult> results = List.of(
                result("가온테크 채용 소식", "가온테크가 신입 개발자를 채용한다."),
                result("(주)다른물산 신규 채용 공고", "유통 기업 다른물산이 채용을 시작했다."));

        List<CompanyWebSearchResult> kept =
                resolver.filterObviousMismatches(new CompanyIdentity("가온테크", "IT", "서울"), results);

        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).title()).isEqualTo("가온테크 채용 소식");
    }

    /** 회사명 미등장은 "판별 불가"라 유지 — 공식 사이트·브랜드/서비스명 중심 결과 보호(과도 제거 방지). */
    @Test
    void nameAbsentWithoutExplicitOtherCompanyIsKept() {
        List<CompanyWebSearchResult> results = List.of(
                result("서비스 점검 안내", "금일 새벽 텀블벅 서비스 점검이 진행됩니다."),
                result("회사소개 | 홈", "고객과 함께 성장하는 IT 파트너"));

        assertThat(resolver.filterObviousMismatches(new CompanyIdentity("가온테크", "IT", "서울"), results))
                .hasSize(2);
    }

    /** 제목의 법인 표기 상호가 대상 회사명과 부분 일치하면 확신이 없으므로 유지. */
    @Test
    void titleWithOverlappingCorporateNameIsKept() {
        List<CompanyWebSearchResult> results = List.of(
                result("(주)가온 신규 서비스 공개", "클라우드 신규 서비스를 공개했다."));

        assertThat(resolver.filterObviousMismatches(new CompanyIdentity("가온테크", "", ""), results))
                .hasSize(1);
    }

    @Test
    void corporateMarkersAndSpacingDoNotCauseExclusion() {
        List<CompanyWebSearchResult> results = List.of(
                result("(주)가온 테크, 상반기 실적 발표", "주식회사 가온테크 관련 기사"));

        List<CompanyWebSearchResult> kept =
                resolver.filterObviousMismatches(new CompanyIdentity("주식회사 가온테크", "", ""), results);

        assertThat(kept).hasSize(1);
    }

    @Test
    void mentionOnlyInDescriptionIsKept() {
        List<CompanyWebSearchResult> results = List.of(
                result("업계 동향 리포트", "이번 분기에는 가온테크 등 중견 IT 기업의 채용이 늘었다."));

        assertThat(resolver.filterObviousMismatches(new CompanyIdentity("가온테크", "", ""), results))
                .hasSize(1);
    }

    /** 중간 강도: 이름을 포함하는 더 긴 상호(부분 일치)는 확신이 없으므로 제거하지 않는다. */
    @Test
    void longerSimilarNameIsKeptNotOverRemoved() {
        List<CompanyWebSearchResult> results = List.of(
                result("가온테크놀로지 신제품 공개", "가온테크놀로지가 신제품을 공개했다."));

        assertThat(resolver.filterObviousMismatches(new CompanyIdentity("가온테크", "", ""), results))
                .hasSize(1);
    }

    @Test
    void unknownCompanyNameDisablesFilteringInsteadOfRemovingAll() {
        List<CompanyWebSearchResult> results = List.of(
                result("아무 기사", "회사명 없는 스니펫"));

        assertThat(resolver.filterObviousMismatches(new CompanyIdentity("", "IT", "서울"), results))
                .hasSize(1);
    }

    // ── 이름 정규화 ──

    @Test
    void normalizeCompanyNameStripsCorporateMarkersAndSymbols() {
        assertThat(resolver.normalizeCompanyName("(주)가온 테크")).isEqualTo("가온테크");
        assertThat(resolver.normalizeCompanyName("주식회사 가온테크")).isEqualTo("가온테크");
        assertThat(resolver.normalizeCompanyName("㈜가온테크")).isEqualTo("가온테크");
        assertThat(resolver.normalizeCompanyName("Gaon Tech Co., Ltd.")).isEqualTo("gaontechcoltd");
    }
}
