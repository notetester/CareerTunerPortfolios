package com.careertuner.admin.common.grid;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 목록 요청 1차 정규화(요청 화이트리스트) 계층.
 *
 * <p>3계층 방어의 첫 단계: ①여기서 searchType/sortBy/sortDir/size/필터 값을
 * 스펙 화이트리스트로 보정 → ②서비스가 count 후 page 클램프 → ③XML {@code <choose>}
 * ORDER BY 화이트리스트 매핑. 허용되지 않는 값은 예외 대신 기본값으로 조용히 보정한다
 * (관리자 화면 UX 상 잘못된 URL 공유가 에러로 끝나지 않게).</p>
 */
public final class AdminListNormalizer {

    private static final int KEYWORD_MAX_LENGTH = 100;

    private AdminListNormalizer() {
    }

    public static AdminListQuery normalize(AdminListRequest request, AdminGridSpec spec) {
        String keyword = normalizeKeyword(request.getKeyword());
        String searchType = normalizeSearchType(request.getSearchType(), spec);
        Map<String, String> filters = normalizeFilters(request.getFilters(), spec);

        LocalDate from = parseDate(request.getDateFrom());
        LocalDate to = parseDate(request.getDateTo());
        // 기간 교환: from > to 로 오면 서로 바꿔서 항상 유효한 구간으로 만든다.
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        LocalDate toExclusive = to == null ? null : to.plusDays(1);

        String sortBy = normalizeSortBy(request.getSortBy(), spec);
        String sortDir = normalizeSortDir(request.getSortDir(), spec);
        String mode = "CLIENT".equalsIgnoreCase(nullSafe(request.getMode())) ? "CLIENT" : "SERVER";

        int page;
        int size;
        if ("CLIENT".equals(mode)) {
            // CLIENT 모드는 상한 내 전량 1회 반환 — 페이징 파라미터는 무시한다.
            page = 1;
            size = spec.clientMaxSize();
        } else {
            page = Math.max(request.getPage(), 1);
            size = spec.pageSizes().contains(request.getSize()) ? request.getSize() : spec.defaultPageSize();
        }

        return new AdminListQuery(keyword, searchType, filters, from, toExclusive,
                sortBy, sortDir, mode, page, size);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.length() > KEYWORD_MAX_LENGTH ? trimmed.substring(0, KEYWORD_MAX_LENGTH) : trimmed;
    }

    private static String normalizeSearchType(String searchType, AdminGridSpec spec) {
        if (searchType != null && spec.searchTypes().contains(searchType.trim())) {
            return searchType.trim();
        }
        return spec.defaultSearchType();
    }

    private static Map<String, String> normalizeFilters(Map<String, String> raw, AdminGridSpec spec) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, Set<String>> allowed : spec.filters().entrySet()) {
            String value = raw.get(allowed.getKey());
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            if (allowed.getValue().contains(trimmed)) {
                normalized.put(allowed.getKey(), trimmed);
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (allowed.getValue().contains(upper)) {
                normalized.put(allowed.getKey(), upper);
            }
            // 화이트리스트 밖 값은 조용히 무시(필터 미적용).
        }
        return normalized;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normalizeSortBy(String sortBy, AdminGridSpec spec) {
        if (sortBy != null && spec.sortKeys().contains(sortBy.trim())) {
            return sortBy.trim();
        }
        return spec.defaultSortBy();
    }

    private static String normalizeSortDir(String sortDir, AdminGridSpec spec) {
        if (sortDir != null) {
            String upper = sortDir.trim().toUpperCase(Locale.ROOT);
            if ("ASC".equals(upper) || "DESC".equals(upper)) {
                return upper;
            }
        }
        return spec.defaultSortDir();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}
