package com.careertuner.companyanalysis.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 기업분석 웹검색 결과 캐시 row(235 §4·§6 · D-4a). 같은 회사 재검색 방지·신선도 판정 근거.
 *
 * <p>{@code results} 는 웹검색 스니펫+URL 목록(D-1 CompanyWebSearchResult / D-2 CompanyWebEvidence)의
 * JSON 문자열이다. company_analysis 의 JSON 컬럼과 동일하게 매퍼는 문자열로 다룬다.
 * {@code fetchedAt} 은 수집 시각으로 TTL(7일)·재조회 판정 기준, {@code createdAt} 은 최초 생성 시각으로
 * upsert 시 보존한다(갱신 금지).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySearchCache {

    private Long id;
    private String queryKey;
    private String results;
    private LocalDateTime fetchedAt;
    private LocalDateTime createdAt;
}
