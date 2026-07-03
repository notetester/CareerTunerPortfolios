package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.careertuner.applicationcase.domain.ApplicationCase;
import com.careertuner.applicationcase.service.AiUsageLogService;
import com.careertuner.applicationcase.service.ApplicationCaseAccessService;
import com.careertuner.applicationcase.service.ApplicationCaseAnalysisStatusService;
import com.careertuner.applicationcase.service.BAnalysisGenerationService;
import com.careertuner.applicationcase.service.BAnalysisJsonValidator;
import com.careertuner.companyanalysis.domain.CompanySearchCache;
import com.careertuner.companyanalysis.mapper.CompanyAnalysisMapper;
import com.careertuner.companyanalysis.mapper.CompanySearchCacheMapper;
import com.careertuner.companyanalysis.websearch.CompanyEvidenceCollector;
import com.careertuner.companyanalysis.websearch.CompanyIdentity;
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchException;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchProperties;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchResult;
import com.careertuner.companyanalysis.websearch.NaverSearchCategory;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

/**
 * 웹검색 배선 검증(비R1·비네트워크 · D-4b/D-4c). flag OFF 불변, MISS 검색·정제·캐시 put, HIT 검색 미호출,
 * 빈결과도 put→재조회 HIT, query_key 회사 단위 정규화, 그리고 D-4c 의 검색 실패 degrade(공고-only 후퇴·
 * 실패 시 put 미호출)·비용 상한(검색 호출/결과 수)을 collectWebEvidence 직접 호출로 고정한다.
 * 웹검색 client 는 mock(실호출 없음), 캐시는 in-memory mapper 로 put 지속을 재현한다.
 */
class CompanyAnalysisServiceWebSearchWiringTest {

    private final CompanyWebSearchClient webSearchClient = mock(CompanyWebSearchClient.class);
    private final CompanySourceResolver sourceResolver = new CompanySourceResolver();
    private final CompanyEvidenceCollector evidenceCollector = new CompanyEvidenceCollector(sourceResolver);
    private final InMemoryCacheMapper cacheMapper = new InMemoryCacheMapper();
    private final CompanySearchCacheService cacheService = new CompanySearchCacheService(cacheMapper);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CompanyAnalysisService service(CompanyWebSearchProperties properties) {
        return new CompanyAnalysisService(
                mock(ApplicationCaseAccessService.class),
                mock(CompanyAnalysisMapper.class),
                mock(BAnalysisGenerationService.class),
                mock(AiUsageLogService.class),
                mock(ApplicationCaseAnalysisStatusService.class),
                mock(TransactionTemplate.class),
                mock(BAnalysisJsonValidator.class),
                mock(BCompanyAnalysisCanonicalizer.class),
                mock(NotificationService.class),
                properties,
                sourceResolver,
                webSearchClient,
                evidenceCollector,
                cacheService,
                objectMapper);
    }

    private static CompanyWebSearchProperties enabled(boolean on) {
        CompanyWebSearchProperties p = new CompanyWebSearchProperties();
        p.setEnabled(on);
        return p;
    }

    private static ApplicationCase applicationCase(String companyName) {
        return ApplicationCase.builder().id(10L).userId(1L)
                .companyName(companyName).jobTitle("시스템엔지니어").status("DRAFT").build();
    }

    private static CompanyWebSearchResult result(String title, String link, String desc) {
        return new CompanyWebSearchResult(NaverSearchCategory.NEWS, title, link, desc, Instant.parse("2026-07-03T00:00:00Z"));
    }

    // ── flag OFF 불변 ──

    @Test
    void flagOffReturnsEmptyAndTouchesNoSearchOrCache() {
        CompanyAnalysisService service = service(enabled(false));

        List<CompanyWebEvidence> evidence = service.collectWebEvidence(applicationCase("가온테크"));

        assertThat(evidence).isEmpty();
        verifyNoInteractions(webSearchClient);
        assertThat(cacheMapper.store).isEmpty();
    }

    @Test
    void flagOnWithBlankCompanyNameReturnsEmptyWithoutSearch() {
        CompanyAnalysisService service = service(enabled(true));

        assertThat(service.collectWebEvidence(applicationCase("   "))).isEmpty();
        verifyNoInteractions(webSearchClient);
        assertThat(cacheMapper.store).isEmpty();
    }

    // ── flag ON + MISS: 검색·정제·캐시 put ──

    @Test
    void flagOnMissFiltersMismatchExcludesBlankUrlDedupsAndCachesCleanedResults() {
        CompanyAnalysisService service = service(enabled(true));
        // 같은 리스트를 모든 카테고리에서 반환: 정상 R1, 트레일링슬래시 중복(R1과 정규화 동일),
        // blank URL(제외), 동명 불일치(제목이 (주)다른회사 명시 → 제외).
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("가온테크 신규 서비스", "https://news.example.com/1", "가온테크가 서비스를 출시했다"),
                result("가온테크 속보", "https://news.example.com/1/", "가온테크 관련 속보"),
                result("가온테크 링크없음", "", "가온테크 관련이지만 URL 없음"),
                result("(주)다른회사 채용", "https://other.example.com/x", "다른회사 채용 소식")));

        List<CompanyWebEvidence> evidence = service.collectWebEvidence(applicationCase("가온테크"));

        // 캐시에는 정제된 결과(중복·blank·동명 제거)만 저장 — R1 하나.
        CompanySearchCache cached = cacheMapper.store.get("가온테크");
        assertThat(cached).isNotNull();
        CompanyWebSearchResult[] storedResults = objectMapper.readValue(cached.getResults(), CompanyWebSearchResult[].class);
        assertThat(storedResults).hasSize(1);
        assertThat(storedResults[0].link()).isEqualTo("https://news.example.com/1");
        // evidence 도 정제 결과 기준으로 생성.
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).url()).isEqualTo("https://news.example.com/1");
    }

    // ── flag ON + HIT: 검색 미호출 ──

    @Test
    void flagOnHitReusesCacheWithoutSearchingAgain() {
        CompanyAnalysisService service = service(enabled(true));
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("가온테크 뉴스", "https://news.example.com/1", "가온테크 관련")));

        service.collectWebEvidence(applicationCase("가온테크")); // MISS → 검색 + put
        clearInvocations(webSearchClient);

        List<CompanyWebEvidence> second = service.collectWebEvidence(applicationCase("가온테크")); // HIT

        verify(webSearchClient, never()).search(any(), any());
        assertThat(second).hasSize(1);
        assertThat(second.get(0).url()).isEqualTo("https://news.example.com/1");
    }

    // ── flag ON + MISS + 빈결과: "[]" put → 재조회 HIT ──

    @Test
    void flagOnEmptySearchStillCachesEmptyArrayAndSecondCallHits() {
        CompanyAnalysisService service = service(enabled(true));
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of());

        List<CompanyWebEvidence> first = service.collectWebEvidence(applicationCase("가온테크"));

        assertThat(first).isEmpty();
        CompanySearchCache cached = cacheMapper.store.get("가온테크");
        assertThat(cached).isNotNull();
        assertThat(cached.getResults()).isEqualTo("[]");
        // put(..., null) → CompanySearchCacheService 의 주입 Clock 이 fetchedAt 을 채운다(직접 now() 미사용).
        assertThat(cached.getFetchedAt()).isNotNull();

        clearInvocations(webSearchClient);
        List<CompanyWebEvidence> second = service.collectWebEvidence(applicationCase("가온테크"));

        assertThat(second).isEmpty();
        verify(webSearchClient, never()).search(any(), any()); // 빈결과도 HIT → 재검색 없음
    }

    // ── D-4c degrade: 검색 실패 → 공고-only 후퇴, 실패는 캐시에 굳히지 않음 ──

    @Test
    void searchFailureDegradesToJobPostingOnlyWithoutThrowing() {
        CompanyAnalysisService service = service(enabled(true));
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class)))
                .thenThrow(new CompanyWebSearchException("네이버 검색 요청이 실패했습니다. status=500, errorCode=unknown (category=NEWS)"));

        assertThatCode(() -> {
            List<CompanyWebEvidence> evidence = service.collectWebEvidence(applicationCase("가온테크"));
            assertThat(evidence).isEmpty(); // 공고-only 후퇴(빈 목록) — 분석은 계속된다.
        }).doesNotThrowAnyException();
    }

    /** ★ 검색 예외 시 실패를 "[]" 로 캐시에 굳히지 않는다(put 미호출) — 7일 HIT 방지. */
    @Test
    void searchFailureDoesNotWriteEmptyResultToCache() {
        CompanyAnalysisService service = service(enabled(true));
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class)))
                .thenThrow(new CompanyWebSearchException("네이버 검색 API와 통신하지 못했습니다. (category=NEWS)"));

        service.collectWebEvidence(applicationCase("가온테크"));

        assertThat(cacheMapper.store).isEmpty(); // put 이 실행되지 않아 캐시가 비어 있다.
    }

    /** 정상 검색이 0건이면 기존 D-4b 계약대로 "[]" put 을 유지한다(실패 degrade 와 구분). */
    @Test
    void emptySuccessfulSearchStillCachesEmptyUnlikeFailureDegrade() {
        CompanyAnalysisService service = service(enabled(true));
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of());

        service.collectWebEvidence(applicationCase("가온테크"));

        assertThat(cacheMapper.store.get("가온테크")).isNotNull();
        assertThat(cacheMapper.store.get("가온테크").getResults()).isEqualTo("[]");
    }

    // ── D-4c 비용 상한: 검색 호출 수·결과 수 상한을 프로퍼티로 조정 ──

    @Test
    void searchCallCapLimitsInvocationsPerAnalysis() {
        CompanyWebSearchProperties properties = enabled(true);
        properties.setMaxSearchCallsPerAnalysis(2);
        CompanyAnalysisService service = service(properties);
        // 카테고리마다 고유 URL 1건 → 결과 상한엔 안 걸리고 호출 상한만 작동.
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class)))
                .thenAnswer(invocation -> List.of(result("가온테크",
                        "https://news.example.com/" + System.nanoTime(), "가온테크 관련")));

        service.collectWebEvidence(applicationCase("가온테크"));

        verify(webSearchClient, times(2)).search(any(NaverSearchCategory.class), any(String.class));
    }

    @Test
    void resultCapStopsEarlyWhenEnoughResultsCollected() {
        CompanyWebSearchProperties properties = enabled(true);
        properties.setMaxResultsPerAnalysis(1);
        CompanyAnalysisService service = service(properties);
        // 첫 호출에서 결과 상한(1) 도달 → 다음 카테고리 조회 전에 조기 중단.
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("가온테크", "https://news.example.com/1", "가온테크 관련")));

        service.collectWebEvidence(applicationCase("가온테크"));

        verify(webSearchClient, times(1)).search(any(NaverSearchCategory.class), any(String.class));
    }

    /** ★ 한 호출이 상한을 넘는 건수를 반환해도 결과 상한을 초과 저장하지 않는다(리뷰 반영). */
    @Test
    void resultCapLimitsStoredResultsWhenSingleCallOverflows() {
        CompanyWebSearchProperties properties = enabled(true);
        properties.setMaxResultsPerAnalysis(1);
        CompanyAnalysisService service = service(properties);
        // 첫 호출이 고유 URL 2건 반환 — 상한 1 이므로 1건만 저장돼야 한다.
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("가온테크 1", "https://news.example.com/1", "가온테크 관련 1"),
                result("가온테크 2", "https://news.example.com/2", "가온테크 관련 2")));

        List<CompanyWebEvidence> evidence = service.collectWebEvidence(applicationCase("가온테크"));

        CompanyWebSearchResult[] cached = objectMapper.readValue(
                cacheMapper.store.get("가온테크").getResults(), CompanyWebSearchResult[].class);
        assertThat(cached).hasSize(1);        // 캐시 저장 1건(초과 저장 없음)
        assertThat(evidence).hasSize(1);      // evidence 도 1건
        assertThat(evidence.get(0).url()).isEqualTo("https://news.example.com/1"); // 먼저 수집분 우선
        verify(webSearchClient, times(1)).search(any(NaverSearchCategory.class), any(String.class));
    }

    @Test
    void defaultCostCapsAreFourCallsAndTwelveResults() {
        CompanyWebSearchProperties properties = new CompanyWebSearchProperties();

        assertThat(properties.getMaxSearchCallsPerAnalysis()).isEqualTo(4);
        assertThat(properties.getMaxResultsPerAnalysis()).isEqualTo(12);
    }

    /**
     * 리뷰 반영: 상한이 0/음수로 오설정돼도 flag ON 웹검색이 조용히 꺼지지 않는다(최소 1 클램프).
     * 비활성화는 상한이 아니라 enabled=false 로만 한다.
     */
    @Test
    void zeroOrNegativeCostCapsAreClampedSoWebSearchStaysActive() {
        CompanyWebSearchProperties properties = enabled(true);
        properties.setMaxSearchCallsPerAnalysis(0);
        properties.setMaxResultsPerAnalysis(-5);
        CompanyAnalysisService service = service(properties);
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("가온테크", "https://news.example.com/1", "가온테크 관련")));

        List<CompanyWebEvidence> evidence = service.collectWebEvidence(applicationCase("가온테크"));

        // 클램프 덕에 최소 1회 검색·1건 결과 → 웹검색이 무효화되지 않는다.
        verify(webSearchClient, times(1)).search(any(NaverSearchCategory.class), any(String.class));
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).url()).isEqualTo("https://news.example.com/1");
    }

    // ── query_key 회사 단위 정규화 ──

    private String cacheKey(String companyName) {
        return service(enabled(false)).companyCacheKey(new CompanyIdentity(companyName, "", ""));
    }

    @Test
    void companyCacheKeyNormalizesRepresentationDifferences() {
        String base = cacheKey("Gaon Tech");

        assertThat(cacheKey("  Gaon   Tech  ")).isEqualTo(base);
        assertThat(cacheKey("GAON TECH")).isEqualTo(base);
    }

    /** 법인표기 차이를 같은 회사로 묶는다(D-1 normalizeCompanyName 공유 — 리뷰 반영). */
    @Test
    void companyCacheKeyMergesCorporateMarkerVariants() {
        String base = cacheKey("가온테크");

        assertThat(cacheKey("(주) 가온테크")).isEqualTo(base);
        assertThat(cacheKey("㈜가온테크")).isEqualTo(base);
        assertThat(cacheKey("주식회사 가온테크")).isEqualTo(base);
    }

    /** 234 §7: 업종/지역 힌트는 검색 query 에만 쓰고 cache key 에는 포함하지 않는다. */
    @Test
    void companyCacheKeyIgnoresIndustryAndRegionHints() {
        CompanyAnalysisService service = service(enabled(false));

        assertThat(service.companyCacheKey(new CompanyIdentity("가온테크", "IT 서비스", "서울")))
                .isEqualTo(service.companyCacheKey(new CompanyIdentity("가온테크", "", "")));
    }

    /** put 은 in-memory 저장하고 createdAt 은 최초 생성 시각을 보존하는 최소 fake(계약 재현). */
    private static final class InMemoryCacheMapper implements CompanySearchCacheMapper {
        private final Map<String, CompanySearchCache> store = new HashMap<>();

        @Override
        public void upsertSearchCache(CompanySearchCache cache) {
            CompanySearchCache existing = store.get(cache.getQueryKey());
            store.put(cache.getQueryKey(), CompanySearchCache.builder()
                    .id(existing != null ? existing.getId() : (long) (store.size() + 1))
                    .queryKey(cache.getQueryKey())
                    .results(cache.getResults())
                    .fetchedAt(cache.getFetchedAt())
                    .createdAt(existing != null ? existing.getCreatedAt() : cache.getFetchedAt())
                    .build());
        }

        @Override
        public CompanySearchCache findByQueryKey(String queryKey) {
            return store.get(queryKey);
        }
    }
}
