package com.careertuner.admin.common.grid;

import java.util.List;

/**
 * 관리자 목록 공통 페이징 응답. {@code ApiResponse<PageResult<T>>} 형태로 감싸 반환한다.
 */
public record PageResult<T>(List<T> items, long total, int page, int size, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        return new PageResult<>(items, total, page, size, totalPages(total, size));
    }

    /** 총건수/사이즈 기준 전체 페이지 수. size 가 0 이하이면 0. */
    public static int totalPages(long total, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }
}
