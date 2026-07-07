package com.careertuner.admin.common.grid;

import java.util.Map;
import java.util.Set;

/**
 * 도메인별 그리드 화이트리스트 정의.
 *
 * <p>searchType/sortBy/filters 허용값을 도메인이 선언하면
 * {@link AdminListNormalizer} 가 이 스펙으로 요청을 보정한다.
 * sortBy 는 여기서 키만 검증하고, 실제 컬럼 매핑은 mapper XML 의
 * {@code <choose>} ORDER BY 화이트리스트가 담당한다(이중 방어).</p>
 */
public record AdminGridSpec(
        Set<String> searchTypes,
        String defaultSearchType,
        Set<String> sortKeys,
        String defaultSortBy,
        String defaultSortDir,
        Map<String, Set<String>> filters,
        Set<Integer> pageSizes,
        int defaultPageSize,
        int clientMaxSize,
        int exportMaxRows,
        int selectedIdsMax,
        int bulkIdsMax) {

    /** 공통 기본값(사이즈 {10,20,50,100}, CLIENT/내보내기 상한 10000)을 채운 스펙 생성. */
    public static AdminGridSpec of(Set<String> searchTypes,
                                   Set<String> sortKeys,
                                   String defaultSortBy,
                                   String defaultSortDir,
                                   Map<String, Set<String>> filters) {
        return new AdminGridSpec(
                searchTypes,
                "all",
                sortKeys,
                defaultSortBy,
                defaultSortDir,
                filters,
                Set.of(10, 20, 50, 100),
                20,
                10_000,
                10_000,
                1_000,
                200);
    }
}
