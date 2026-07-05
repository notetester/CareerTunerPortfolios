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

    // ── 양성 정체성 판정(코퍼스 게이트용 · D-6 이슈A) ──

    /** 정규화한 대상 회사명이 제목에 등장하면 양성. */
    @Test
    void positiveMatchWhenNameAppearsInTitle() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.identifiesCompany(identity, result("가온테크 채용 소식", "신입 개발자 채용")))
                .isTrue();
    }

    /** 설명(스니펫)에만 등장해도 양성 — 짧은 제목·브랜드 중심 결과 보호. */
    @Test
    void positiveMatchWhenNameAppearsInDescriptionOnly() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.identifiesCompany(identity, result("업계 동향", "가온테크 등 중견 IT 기업 채용 증가")))
                .isTrue();
    }

    /** 제목의 법인 표기 상호가 대상과 포함관계면 양성. */
    @Test
    void positiveMatchWhenTitleCorporateNameContainsTarget() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.identifiesCompany(identity, result("(주)가온테크 상장 추진", "코스닥 상장을 준비한다")))
                .isTrue();
    }

    /** 접두만 공유하는 대기업(가온전선)만 있는 결과는 양성 아님. */
    @Test
    void noPositiveMatchWhenOnlyPrefixSharingCompanyPresent() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.identifiesCompany(identity, result("가온전선 실적 발표", "가온전선이 분기 실적을 냈다")))
                .isFalse();
    }

    /** 코퍼스에 대상명이 하나도 없으면(전부 접두공유·무관) 양성 근거 없음. */
    @Test
    void corpusHasNoPositiveMatchWhenAllPrefixSharingOrUnrelated() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");
        List<CompanyWebSearchResult> results = List.of(
                result("가온전선 신규 수주", "가온전선이 대형 수주를 따냈다"),
                result("가온칩스 주가 상승", "가온칩스 반도체 팹리스 소식"),
                result("업계 채용 동향", "올해 IT 기업 채용이 늘었다"));

        assertThat(resolver.hasPositiveIdentityMatch(identity, results)).isFalse();
    }

    /** 코퍼스에 대상명이 한 건이라도 있으면 양성(다른 접두공유 결과 혼재해도). */
    @Test
    void corpusHasPositiveMatchWhenAtLeastOneResultIdentifiesTarget() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");
        List<CompanyWebSearchResult> results = List.of(
                result("가온전선 신규 수주", "가온전선이 대형 수주를 따냈다"),
                result("가온테크 개발자 채용", "가온테크가 신입을 뽑는다"));

        assertThat(resolver.hasPositiveIdentityMatch(identity, results)).isTrue();
    }

    /** 회사명이 없으면(식별 불가) 양성 판정 불가 → false(게이트가 판단을 서비스에 맡기지 않도록). */
    @Test
    void corpusHasNoPositiveMatchWhenCompanyNameBlank() {
        CompanyIdentity identity = new CompanyIdentity("  ", "IT", "서울");

        assertThat(resolver.hasPositiveIdentityMatch(identity, List.of(result("아무 기사", "스니펫"))))
                .isFalse();
    }

    /** 빈 코퍼스는 양성 없음. */
    @Test
    void corpusHasNoPositiveMatchWhenResultsEmpty() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.hasPositiveIdentityMatch(identity, List.of())).isFalse();
    }

    // ── 토큰 접두 관계 anchor 확장(D-6 이슈A 2차 · P1) ──

    /** 브랜드 토큰(위버스)이 대상명(위버스컴퍼니)의 접두면 anchor — 대상명 문자열이 없어도 양성. */
    @Test
    void prefixTokenBrandOnlyIsAnchor() {
        CompanyIdentity identity = new CompanyIdentity("위버스컴퍼니", "", "");

        assertThat(resolver.identifiesCompany(identity, result("위버스 앱 신규 기능", "위버스 플랫폼 업데이트")))
                .isTrue();
    }

    /** 대상명(가온테크)이 제목 토큰(가온테크놀로지)의 접두면 anchor — 더 긴 상호도 양성. */
    @Test
    void targetPrefixOfTitleTokenIsAnchor() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.identifiesCompany(identity, result("가온테크놀로지 신제품 공개", "가온테크놀로지가 공개했다")))
                .isTrue();
    }

    /** 접미 토큰만 겹치는 경우(접두 아님)는 anchor 아님 — "컴퍼니"·"테크" 오양성 방지. */
    @Test
    void suffixTokenOverlapIsNotAnchor() {
        // 대상 "위버스컴퍼니", 제목 토큰 "컴퍼니"는 접미일 뿐 접두 아님 → anchor 아님.
        CompanyIdentity identity = new CompanyIdentity("위버스컴퍼니", "", "");

        assertThat(resolver.identifiesCompany(identity, result("컴퍼니 웨딩홀 예약", "컴퍼니 웨딩홀 안내")))
                .isFalse();
    }

    // ── confusableRival 판정(접두 공유 후 발산 · P2) ──

    /** 접두를 공유하나 발산하는 토큰(가온전선)은 rival. */
    @Test
    void prefixSharingDivergentTokenIsRival() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.isConfusableRival(identity, result("가온전선 실적 발표", "가온전선이 실적을 냈다")))
                .isTrue();
    }

    /** 공통 접두가 없는 다른 브랜드(텀블벅)는 rival 아님 — riding 정당. */
    @Test
    void unrelatedBrandWithoutCommonPrefixIsNotRival() {
        CompanyIdentity identity = new CompanyIdentity("백패커", "", "");

        assertThat(resolver.isConfusableRival(identity, result("텀블벅 서비스 점검", "텀블벅 점검 안내")))
                .isFalse();
    }

    /** anchor 인 결과는 rival 이 아니다(대상명이 함께 나오면 절대 제거 금지). */
    @Test
    void anchorResultIsNeverRival() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");
        // 대상명과 접두공유 경쟁사가 한 제목에 함께 등장 — anchor 이므로 rival 아님.
        CompanyWebSearchResult mixed = result("가온테크·가온전선 비교 기사", "가온테크와 가온전선을 비교한다");

        assertThat(resolver.identifiesCompany(identity, mixed)).isTrue();
        assertThat(resolver.isConfusableRival(identity, mixed)).isFalse();
    }

    /** 접두 관계(접두)인 토큰은 rival 아님(가온테크놀로지). */
    @Test
    void prefixRelatedTokenIsNotRival() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.isConfusableRival(identity, result("가온테크놀로지 신제품", "신제품 공개")))
                .isFalse();
    }

    // ── 조사 정규화: 브랜드 토큰에 붙은 흔한 단일 조사를 떼고 판정(D-6 이슈A 3차) ──

    /** "위버스가"(조사 '가')는 '가'를 떼면 "위버스" → 위버스컴퍼니의 접두 anchor. */
    @Test
    void particleSuffixTokenIsNormalizedForPrefixAnchor() {
        CompanyIdentity identity = new CompanyIdentity("위버스컴퍼니", "", "");

        assertThat(resolver.identifiesCompany(identity, result("위버스가 신규 기능 공개", "새 기능 안내")))
                .isTrue();
        assertThat(resolver.identifiesCompany(identity, result("위버스는 콘서트 오픈", "라이브 안내")))
                .isTrue();
    }

    /** 조사 정규화가 rival 탐지를 약화시키지 않는다 — "가온전선이"→"가온전선"→가온테크 rival 유지. */
    @Test
    void particleNormalizationKeepsRivalDetection() {
        CompanyIdentity identity = new CompanyIdentity("가온테크", "", "");

        assertThat(resolver.isConfusableRival(identity, result("가온전선이 대형 수주", "가온전선이 수주를 따냈다")))
                .isTrue();
    }

    /**
     * len<3 토큰은 조사를 떼지 않는다(2자 토큰 훼손 방지). "이가"(2자, '가'로 끝남)를 "이"로 떼면
     * len<2 로 탈락해 판정에서 사라진다 — 떼지 않아야 브랜드 토큰이 보존된다.
     * 대상 "이가네" 제목에 브랜드 토큰 "이가"가 있으면(이가네의 접두) anchor 로 keep 돼야 한다.
     */
    @Test
    void shortTokensAreNotStrippedOfTrailingParticle() {
        CompanyIdentity identity = new CompanyIdentity("이가네", "", "");

        // '가'를 떼면 "이"(len1)로 탈락 → anchor 못 잡음. 떼지 않으면 "이가"가 "이가네"의 접두 → anchor.
        assertThat(resolver.identifiesCompany(identity, result("이가 신메뉴 출시", "신메뉴 안내")))
                .isTrue();
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
