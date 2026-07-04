package com.careertuner.companyanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.companyanalysis.domain.CompanySearchCache;
import com.careertuner.companyanalysis.mapper.CompanySearchCacheMapper;

/**
 * 캐시 서비스 HIT/MISS·TTL·upsert 계약 단위 테스트(235 §11 · D-4a).
 * 기준 시각을 고정 Clock 으로 박아 TTL 판정을 결정적으로 검증한다(flaky 금지). 매퍼는 mock.
 */
class CompanySearchCacheServiceTest {

    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 3, 12, 0, 0);

    private final CompanySearchCacheMapper mapper = mock(CompanySearchCacheMapper.class);
    private final CompanySearchCacheService service =
            new CompanySearchCacheService(mapper, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZONE));

    private static CompanySearchCache row(String results, LocalDateTime fetchedAt) {
        return CompanySearchCache.builder()
                .id(1L).queryKey("가온테크").results(results).fetchedAt(fetchedAt)
                .createdAt(fetchedAt).build();
    }

    // ── HIT/MISS ──

    @Test
    void hitWhenWithinSevenDays() {
        when(mapper.findByQueryKey("가온테크")).thenReturn(row("[{\"url\":\"x\"}]", NOW.minusDays(3)));

        Optional<CompanySearchCache> result = service.get("가온테크");

        assertThat(result).isPresent();
        assertThat(result.get().getResults()).isEqualTo("[{\"url\":\"x\"}]");
    }

    /** 빈 결과 "[]" 도 HIT — hit/miss 와 empty-result 를 혼동하지 않는다. */
    @Test
    void emptyResultsIsStillHit() {
        when(mapper.findByQueryKey("가온테크")).thenReturn(row("[]", NOW.minusDays(1)));

        Optional<CompanySearchCache> result = service.get("가온테크");

        assertThat(result).isPresent();
        assertThat(result.get().getResults()).isEqualTo("[]");
    }

    /** 정확히 7일 경계는 이내 → HIT. */
    @Test
    void exactlySevenDaysIsHit() {
        when(mapper.findByQueryKey("가온테크")).thenReturn(row("[]", NOW.minusDays(7)));

        assertThat(service.get("가온테크")).isPresent();
    }

    /** 7일 초과만 MISS. */
    @Test
    void missWhenOlderThanSevenDays() {
        when(mapper.findByQueryKey("가온테크"))
                .thenReturn(row("[{\"url\":\"x\"}]", NOW.minusDays(7).minusSeconds(1)));

        assertThat(service.get("가온테크")).isEmpty();
    }

    @Test
    void missWhenRowAbsent() {
        when(mapper.findByQueryKey("없는회사")).thenReturn(null);

        assertThat(service.get("없는회사")).isEmpty();
    }

    @Test
    void blankQueryKeyGetReturnsMissWithoutHittingMapper() {
        assertThat(service.get("  ")).isEmpty();
        assertThat(service.get(null)).isEmpty();
        verify(mapper, never()).findByQueryKey(any());
    }

    // ── put / upsert 계약 ──

    @Test
    void putStoresResultsAndFetchedAtAsIs() {
        LocalDateTime fetchedAt = NOW.minusHours(2);

        service.put("가온테크", "[]", fetchedAt);

        ArgumentCaptor<CompanySearchCache> captor = ArgumentCaptor.forClass(CompanySearchCache.class);
        verify(mapper).upsertSearchCache(captor.capture());
        CompanySearchCache saved = captor.getValue();
        assertThat(saved.getQueryKey()).isEqualTo("가온테크");
        assertThat(saved.getResults()).isEqualTo("[]");
        assertThat(saved.getFetchedAt()).isEqualTo(fetchedAt);
    }

    @Test
    void putDefaultsFetchedAtToClockNowWhenNull() {
        service.put("가온테크", "[{\"url\":\"x\"}]", null);

        ArgumentCaptor<CompanySearchCache> captor = ArgumentCaptor.forClass(CompanySearchCache.class);
        verify(mapper).upsertSearchCache(captor.capture());
        assertThat(captor.getValue().getFetchedAt()).isEqualTo(NOW);
    }

    @Test
    void putRejectsBlankQueryKeyWithoutSaving() {
        assertThatThrownBy(() -> service.put("  ", "[]", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.put(null, "[]", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mapper, never()).upsertSearchCache(any());
    }

    /** put → get 왕복(mock storage 시뮬레이션): 저장한 값이 TTL 이내면 HIT 로 돌아온다. */
    @Test
    void putThenGetRoundTrip() {
        LocalDateTime fetchedAt = NOW.minusDays(2);
        service.put("가온테크", "[{\"url\":\"x\"}]", fetchedAt);
        when(mapper.findByQueryKey("가온테크")).thenReturn(row("[{\"url\":\"x\"}]", fetchedAt));

        Optional<CompanySearchCache> result = service.get("가온테크");

        assertThat(result).isPresent();
        assertThat(result.get().getResults()).isEqualTo("[{\"url\":\"x\"}]");
    }
}
