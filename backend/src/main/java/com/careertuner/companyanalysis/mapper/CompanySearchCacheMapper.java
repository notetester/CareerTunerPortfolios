package com.careertuner.companyanalysis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.companyanalysis.domain.CompanySearchCache;

/**
 * 기업분석 웹검색 캐시 매퍼(235 §6 · D-4a).
 *
 * <p>upsert 계약: query_key 충돌 시 {@code results}·{@code fetched_at} 은 갱신하고
 * {@code created_at} 은 최초 생성 시각으로 보존한다. blank/null query_key 거부는 서비스 계층
 * ({@link com.careertuner.companyanalysis.service.CompanySearchCacheService})에서 처리한다.
 */
@Mapper
public interface CompanySearchCacheMapper {

    void upsertSearchCache(CompanySearchCache cache);

    CompanySearchCache findByQueryKey(@Param("queryKey") String queryKey);
}
