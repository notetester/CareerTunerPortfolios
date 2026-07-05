package com.careertuner.admin.common.grid;

import java.util.Locale;

/** 내보내기 범위. all=전체, search=현재 검색 결과, selected=선택 행, page=현재 페이지. */
public enum ExportScope {

    ALL,
    SEARCH,
    SELECTED,
    PAGE;

    public static ExportScope parse(String value) {
        if (value == null) {
            return SEARCH;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "all" -> ALL;
            case "selected" -> SELECTED;
            case "page" -> PAGE;
            default -> SEARCH;
        };
    }
}
