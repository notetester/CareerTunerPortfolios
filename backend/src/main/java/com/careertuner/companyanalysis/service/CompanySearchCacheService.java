package com.careertuner.companyanalysis.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.careertuner.companyanalysis.domain.CompanySearchCache;
import com.careertuner.companyanalysis.mapper.CompanySearchCacheMapper;

/**
 * 기업분석 웹검색 캐시 서비스(235 §6·§11 · D-4a). WEB 실호출·client·CompanyAnalysisService 배선은 D-4b.
 *
 * <p><b>HIT/MISS 계약:</b> row 가 있고 {@code fetched_at} 이 TTL({@link #TTL} 7일 — 235 §11) 이내면 HIT.
 * results 가 {@code "[]"}(빈 결과)라도 HIT 다. MISS 는 <b>row 없음 또는 fetched_at 이 7일 초과</b>일 때만이다.
 * hit/miss 와 empty-result 를 혼동하지 않는다.
 *
 * <p><b>TTL 판정 시각:</b> {@link Clock} 을 주입받아 판정한다. 프로덕션은 시스템 시계, 테스트는 고정 시계를
 * 넣어 {@code LocalDateTime.now()} 의존 flaky 를 없앤다.
 */
@Service
public class CompanySearchCacheService {

    /** 검색 캐시 TTL — 235 §11 확정(같은 회사 7일 내 재검색 안 함). */
    static final Duration TTL = Duration.ofDays(7);

    private final CompanySearchCacheMapper mapper;
    private final Clock clock;

    @Autowired
    public CompanySearchCacheService(CompanySearchCacheMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    // 테스트 전용 — 기준 시각 고정으로 TTL 판정을 결정적으로 검증한다.
    CompanySearchCacheService(CompanySearchCacheMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 캐시 조회. HIT 면 row 를 담은 Optional, MISS(없음 또는 7일 초과) 면 empty.
     * queryKey 가 blank/null 이면 MISS(empty) 로 취급한다(get 은 예외를 던지지 않는다).
     */
    public Optional<CompanySearchCache> get(String queryKey) {
        if (isBlank(queryKey)) {
            return Optional.empty();
        }
        CompanySearchCache row = mapper.findByQueryKey(queryKey);
        if (row == null || row.getFetchedAt() == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 7일 이내 = HIT(경계 포함). 7일 초과만 MISS.
        if (now.isAfter(row.getFetchedAt().plus(TTL))) {
            return Optional.empty();
        }
        return Optional.of(row);
    }

    /**
     * 캐시 저장(upsert). query_key 충돌 시 results·fetchedAt 갱신, created_at 보존(매퍼 계약).
     * resultsJson="[]" 는 그대로 저장한다. fetchedAt 이 null 이면 현재 시각으로 채운다.
     *
     * @throws IllegalArgumentException queryKey 가 blank/null 인 경우(저장하지 않는다)
     */
    public void put(String queryKey, String resultsJson, LocalDateTime fetchedAt) {
        if (isBlank(queryKey)) {
            throw new IllegalArgumentException("queryKey 는 비어 있을 수 없습니다.");
        }
        CompanySearchCache cache = CompanySearchCache.builder()
                .queryKey(queryKey)
                .results(resultsJson)
                .fetchedAt(fetchedAt != null ? fetchedAt : LocalDateTime.now(clock))
                .build();
        mapper.upsertSearchCache(cache);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
