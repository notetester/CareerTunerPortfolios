package com.careertuner.admin.common.grid;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 정규화가 끝난 목록 질의 값.
 *
 * <p>{@link AdminListNormalizer} 만 생성한다(1차 화이트리스트 통과 보장).
 * 서비스는 count 후 {@link #clampPage(long)} 로 2차 보정(페이지 클램프)을 수행하고
 * {@link #toParams()} 를 매퍼에 넘긴다.</p>
 *
 * <p>sortDir 는 ASC/DESC 로 강제된 값이므로 XML 에서 {@code ${sortDir}} 보간이 허용되는
 * 유일한 값이다(리뷰 체크리스트 항목).</p>
 */
public final class AdminListQuery {

    private final String keyword;
    private final String searchType;
    private final Map<String, String> filters;
    private final LocalDate dateFrom;
    private final LocalDate dateToExclusive;
    private final String sortBy;
    private final String sortDir;
    private final String mode;
    private final int size;
    private int page;

    AdminListQuery(String keyword, String searchType, Map<String, String> filters,
                   LocalDate dateFrom, LocalDate dateToExclusive,
                   String sortBy, String sortDir, String mode, int page, int size) {
        this.keyword = keyword;
        this.searchType = searchType;
        this.filters = filters;
        this.dateFrom = dateFrom;
        this.dateToExclusive = dateToExclusive;
        this.sortBy = sortBy;
        this.sortDir = sortDir;
        this.mode = mode;
        this.page = page;
        this.size = size;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    public String sortBy() {
        return sortBy;
    }

    public String sortDir() {
        return sortDir;
    }

    public boolean client() {
        return "CLIENT".equals(mode);
    }

    /** count 결과 기준으로 존재하지 않는 페이지 요청을 마지막 페이지로 보정한다. */
    public void clampPage(long total) {
        int totalPages = PageResult.totalPages(total, size);
        if (totalPages > 0 && page > totalPages) {
            page = totalPages;
        }
        if (page < 1) {
            page = 1;
        }
    }

    /**
     * 매퍼 전달용 파라미터 맵. 필터는 평탄화되어 키 이름 그대로 들어간다
     * (도메인 스펙에서 base 키(keyword/sortBy 등)와 겹치지 않는 필터 키만 선언할 것).
     */
    public Map<String, Object> toParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("searchType", searchType);
        params.putAll(filters);
        params.put("dateFrom", dateFrom);
        params.put("dateToExclusive", dateToExclusive);
        params.put("sortBy", sortBy);
        params.put("sortDir", sortDir);
        params.put("size", size);
        params.put("offset", (long) (page - 1) * size);
        return params;
    }
}
