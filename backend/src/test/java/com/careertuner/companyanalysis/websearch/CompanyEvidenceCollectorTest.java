package com.careertuner.companyanalysis.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 검색 결과 → WEB 근거 corpus 정규화 단위 테스트 —
 * 동명 불일치 선제거·URL/제목/수집시각 보존·URL 없는 결과 제외·URL 중복 제거.
 */
class CompanyEvidenceCollectorTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-07-03T00:00:00Z");
    private static final CompanyIdentity IDENTITY = new CompanyIdentity("가온테크", "IT 서비스", "서울");

    private final CompanyEvidenceCollector collector =
            new CompanyEvidenceCollector(new CompanySourceResolver());

    private static CompanyWebSearchResult result(NaverSearchCategory category, String title, String link, String description) {
        return new CompanyWebSearchResult(category, title, link, description, FETCHED_AT);
    }

    @Test
    void preservesUrlTitleSnippetAndFetchedAt() {
        List<CompanyWebEvidence> evidences = collector.collect(IDENTITY, List.of(
                result(NaverSearchCategory.NEWS, "가온테크 신규 서비스",
                        "https://news.example.com/1", "가온테크가 클라우드 서비스를 공개했다.")));

        assertThat(evidences).hasSize(1);
        CompanyWebEvidence evidence = evidences.get(0);
        assertThat(evidence.url()).isEqualTo("https://news.example.com/1");
        assertThat(evidence.title()).isEqualTo("가온테크 신규 서비스");
        assertThat(evidence.snippet()).isEqualTo("가온테크가 클라우드 서비스를 공개했다.");
        assertThat(evidence.fetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void excludesObviousNamesakeMismatchBeforeGate() {
        List<CompanyWebEvidence> evidences = collector.collect(IDENTITY, List.of(
                result(NaverSearchCategory.NEWS, "가온테크 채용 소식",
                        "https://news.example.com/1", "가온테크가 채용을 시작했다."),
                result(NaverSearchCategory.NEWS, "(주)다른물산 신규 채용 공고",
                        "https://news.example.com/2", "유통 기업 다른물산이 채용을 시작했다.")));

        assertThat(evidences).hasSize(1);
        assertThat(evidences.get(0).url()).isEqualTo("https://news.example.com/1");
    }

    @Test
    void skipsResultsWithoutUrl() {
        List<CompanyWebEvidence> evidences = collector.collect(IDENTITY, List.of(
                result(NaverSearchCategory.WEBKR, "가온테크 소개", "", "가온테크 회사 소개 페이지"),
                result(NaverSearchCategory.WEBKR, "가온테크 소개", null, "가온테크 회사 소개 페이지")));

        assertThat(evidences).isEmpty();
    }

    @Test
    void dedupsSameUrlAcrossCategories() {
        List<CompanyWebEvidence> evidences = collector.collect(IDENTITY, List.of(
                result(NaverSearchCategory.NEWS, "가온테크 뉴스 제목",
                        "https://example.com/same", "가온테크 뉴스 스니펫"),
                result(NaverSearchCategory.BLOG, "가온테크 블로그 제목",
                        "https://example.com/same", "가온테크 블로그 스니펫")));

        assertThat(evidences).hasSize(1);
        assertThat(evidences.get(0).title()).isEqualTo("가온테크 뉴스 제목");
    }

    @Test
    void emptyOrNullInputReturnsEmpty() {
        assertThat(collector.collect(IDENTITY, List.of())).isEmpty();
        assertThat(collector.collect(IDENTITY, null)).isEmpty();
    }
}
