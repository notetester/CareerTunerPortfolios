package com.careertuner.companyanalysis.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.careertuner.companyanalysis.domain.CompanySearchCache;

/**
 * 검색 캐시 매퍼 XML 실검증(H2 MySQL 모드, D-4a) — insert→select·JSON(CLOB) 컬럼·upsert.
 *
 * <p>운영 upsert 는 MySQL {@code ON DUPLICATE KEY UPDATE} 이므로 임베디드 H2 를 MySQL 모드로 띄워
 * 같은 XML 을 실행한다. 시드 1건(created_at 과거 고정)으로 upsert 갱신 시
 * results·fetched_at 갱신 / created_at 보존 / 같은 row(id 유지)를 결정적으로 확인한다.
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:company_search_cache;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never"
})
@Sql({"/db/testfixture/company_search_cache_schema.sql", "/db/testfixture/company_search_cache_data.sql"})
class CompanySearchCacheMapperIntegrationTest {

    @Autowired
    private CompanySearchCacheMapper mapper;

    @Test
    void insertThenFindByQueryKeyRoundTripsResultsAndFetchedAt() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 7, 3, 10, 0, 0);
        mapper.upsertSearchCache(CompanySearchCache.builder()
                .queryKey("가온테크 IT 서비스")
                .results("[{\"url\":\"https://news.example.com/1\",\"title\":\"가온테크\"}]")
                .fetchedAt(fetchedAt)
                .build());

        CompanySearchCache found = mapper.findByQueryKey("가온테크 IT 서비스");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isNotNull();
        assertThat(found.getResults()).contains("https://news.example.com/1");
        assertThat(found.getFetchedAt()).isEqualTo(fetchedAt);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void emptyResultsArrayIsStoredAsIs() {
        mapper.upsertSearchCache(CompanySearchCache.builder()
                .queryKey("빈결과회사")
                .results("[]")
                .fetchedAt(LocalDateTime.of(2026, 7, 3, 10, 0, 0))
                .build());

        assertThat(mapper.findByQueryKey("빈결과회사").getResults()).isEqualTo("[]");
    }

    @Test
    void findByQueryKeyReturnsNullWhenAbsent() {
        assertThat(mapper.findByQueryKey("존재하지않는쿼리")).isNull();
    }

    /** upsert 계약: query_key 충돌 시 results·fetched_at 갱신, created_at 보존, 같은 row(id 유지). */
    @Test
    void upsertOnConflictUpdatesResultsAndFetchedAtButPreservesCreatedAt() {
        CompanySearchCache seed = mapper.findByQueryKey("seed-company");
        assertThat(seed).isNotNull();
        Long seedId = seed.getId();
        LocalDateTime seedCreatedAt = seed.getCreatedAt();
        assertThat(seedCreatedAt).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 0, 0));

        LocalDateTime newFetchedAt = LocalDateTime.of(2026, 7, 3, 15, 30, 0);
        mapper.upsertSearchCache(CompanySearchCache.builder()
                .queryKey("seed-company")
                .results("[]")
                .fetchedAt(newFetchedAt)
                .build());

        CompanySearchCache updated = mapper.findByQueryKey("seed-company");
        assertThat(updated.getId()).isEqualTo(seedId);                 // 같은 row 갱신(delete+insert 아님)
        assertThat(updated.getResults()).isEqualTo("[]");              // results 갱신
        assertThat(updated.getFetchedAt()).isEqualTo(newFetchedAt);    // fetched_at 갱신
        assertThat(updated.getCreatedAt()).isEqualTo(seedCreatedAt);   // created_at 보존(갱신 금지)
    }
}
