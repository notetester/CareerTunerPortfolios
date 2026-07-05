package com.careertuner.admin.common.grid;

import java.util.function.Function;

/**
 * 내보내기 컬럼 정의. 헤더 문자열 + 행에서 값을 꺼내는 추출자 하나로
 * CSV/Excel 을 겸용한다(도메인별 수기 셀 매핑 반복 제거).
 */
public record ExportColumn<T>(String header, Function<T, Object> extractor) {

    public static <T> ExportColumn<T> of(String header, Function<T, Object> extractor) {
        return new ExportColumn<>(header, extractor);
    }
}
