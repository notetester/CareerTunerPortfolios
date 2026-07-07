package com.careertuner.admin.common.grid;

import java.util.Locale;

/** 내보내기 파일 형식. */
public enum ExportFormat {

    CSV("text/csv; charset=UTF-8", "csv"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** csv/excel/xlsx 를 허용하고 그 외는 CSV 로 보정한다. */
    public static ExportFormat parse(String value) {
        if (value == null) {
            return CSV;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "excel", "xlsx" -> EXCEL;
            default -> CSV;
        };
    }
}
