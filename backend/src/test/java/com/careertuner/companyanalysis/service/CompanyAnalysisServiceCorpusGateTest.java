package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.careertuner.companyanalysis.websearch.CompanySourceResolver;
import com.careertuner.companyanalysis.websearch.CompanyWebEvidence;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchClient;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchProperties;
import com.careertuner.companyanalysis.websearch.CompanyWebSearchResult;
import com.careertuner.companyanalysis.websearch.NaverSearchCategory;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

/**
 * 코퍼스 레벨 회사 정체성 게이트 검증(D-6 이슈A · 동명 접두충돌). flag ON 에서 수집된 검색결과 중
 * 대상 회사를 양성 식별하는 결과가 하나도 없으면 webEvidence 를 빈 목록으로 degrade 해 공고-only 로
 * 후퇴하는지, 양성 근거가 하나라도 있으면 기존 동작을 유지하는지를 collectWebEvidence 직접 호출로 고정한다.
 * 웹검색 client 는 mock(실호출 없음), resolver·collector 는 실제 구현, 캐시는 in-memory 다.
 */
class CompanyAnalysisServiceCorpusGateTest {

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

    private static CompanyWebSearchProperties enabled() {
        CompanyWebSearchProperties p = new CompanyWebSearchProperties();
        p.setEnabled(true);
        return p;
    }

    private static ApplicationCase applicationCase(String companyName) {
        return ApplicationCase.builder().id(10L).userId(1L)
                .companyName(companyName).jobTitle("시스템엔지니어").status("DRAFT").build();
    }

    private static CompanyWebSearchResult result(String title, String link, String desc) {
        return new CompanyWebSearchResult(NaverSearchCategory.NEWS, title, link, desc, Instant.parse("2026-07-03T00:00:00Z"));
    }

    // ── degrade: 코퍼스에 대상 양성 근거가 하나도 없으면 공고-only 로 후퇴 ──

    /**
     * "가온테크" 검색이 접두공유 대기업·무관 결과로만 채워짐(대상명 미등장, 법인표기 marker 없음 — 실검색 형태).
     * 코퍼스 게이트가 양성 근거 부재를 감지해 evidence 를 빈 목록으로 degrade 한다.
     */
    @Test
    void degradesToEmptyWhenNoResultIdentifiesTargetCompany() {
        CompanyAnalysisService service = service(enabled());
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("가온전선 신규 수주 소식", "https://news.example.com/1", "가온전선이 대형 수주를 따냈다"),
                result("가온칩스 주가 상승", "https://news.example.com/2", "가온칩스 반도체 팹리스 소식"),
                result("올해 IT 채용 동향", "https://news.example.com/3", "업계 전반 채용이 늘었다")));

        List<CompanyWebEvidence> evidence = service.collectWebEvidence(applicationCase("가온테크"));

        assertThat(evidence).isEmpty();
    }

    // ── keep: 대상 양성 근거가 하나라도 있으면 전체 유지(riding-along 허용) ──

    /**
     * "위버스컴퍼니": 최소 1건이 대상명 포함(+브랜드 "위버스"만 있는 결과 혼재) → degrade 안 함, 전체 유지.
     */
    @Test
    void keepsCorpusWhenAtLeastOneResultIdentifiesTargetAmongBrandOnly() {
        CompanyAnalysisService service = service(enabled());
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("위버스컴퍼니 채용 공고", "https://news.example.com/1", "위버스컴퍼니가 개발자를 채용한다"),
                result("위버스 앱 업데이트", "https://news.example.com/2", "위버스 플랫폼 신규 기능 출시")));

        List<CompanyWebEvidence> evidence = service.collectWebEvidence(applicationCase("위버스컴퍼니"));

        assertThat(evidence).hasSize(2);
    }

    /** "딥그로브" 회사명이 최소 1건에 등장 → 유지. */
    @Test
    void keepsCorpusForDeepgroveWhenNamePresent() {
        CompanyAnalysisService service = service(enabled());
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("딥그로브 시리즈A 투자 유치", "https://news.example.com/1", "AI 스타트업 딥그로브가 투자를 받았다")));

        assertThat(service.collectWebEvidence(applicationCase("딥그로브"))).hasSize(1);
    }

    /** "백패커"(아이디어스 운영사) 회사명이 최소 1건에 등장 → 유지. */
    @Test
    void keepsCorpusForBackpackrWhenNamePresent() {
        CompanyAnalysisService service = service(enabled());
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("백패커 아이디어스 거래액 성장", "https://news.example.com/1", "백패커가 운영하는 아이디어스가 성장했다"),
                result("핸드메이드 시장 동향", "https://news.example.com/2", "수공예 커머스가 주목받는다")));

        assertThat(service.collectWebEvidence(applicationCase("백패커"))).hasSize(2);
    }

    /** "동국제약" 회사명이 최소 1건에 등장 → 유지. */
    @Test
    void keepsCorpusForDongkookWhenNamePresent() {
        CompanyAnalysisService service = service(enabled());
        when(webSearchClient.search(any(NaverSearchCategory.class), any(String.class))).thenReturn(List.of(
                result("동국제약 신제품 출시", "https://news.example.com/1", "동국제약이 신약을 출시했다")));

        assertThat(service.collectWebEvidence(applicationCase("동국제약"))).hasSize(1);
    }

    /** flag OFF 는 게이트와 무관하게 항상 빈 목록(검색 미호출). */
    @Test
    void flagOffStaysEmptyRegardlessOfGate() {
        CompanyWebSearchProperties off = new CompanyWebSearchProperties();
        off.setEnabled(false);
        CompanyAnalysisService service = service(off);

        assertThat(service.collectWebEvidence(applicationCase("가온테크"))).isEmpty();
    }

    /** put 은 in-memory 저장, createdAt 은 최초 생성 시각 보존(계약 재현 최소 fake). */
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
