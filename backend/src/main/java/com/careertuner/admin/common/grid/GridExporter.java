package com.careertuner.admin.common.grid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 관리자 그리드 공통 내보내기 빌더.
 *
 * <p>{@link ExportColumn} 목록 하나로 CSV(UTF-8 BOM + RFC4180 이스케이프)와
 * Excel(Apache POI, SXSSF 스트리밍 xlsx)을 함께 생성한다.</p>
 */
public final class GridExporter {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    /** SXSSF 메모리 유지 행 수(초과분은 임시 파일로 흘려보냄). */
    private static final int EXCEL_WINDOW_SIZE = 200;

    private GridExporter() {
    }

    /** 형식에 맞는 바이트를 생성해 첨부 다운로드 응답으로 감싼다. */
    public static <T> ResponseEntity<byte[]> download(String filenameBase, ExportFormat format,
                                                      List<ExportColumn<T>> columns, List<T> rows) {
        byte[] body = format == ExportFormat.EXCEL
                ? toExcel(filenameBase, columns, rows)
                : toCsv(columns, rows);
        String filename = "%s_%s.%s".formatted(filenameBase,
                LocalDateTime.now().format(FILE_STAMP_FORMAT), format.extension());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, format.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /** UTF-8 BOM + RFC4180 이스케이프 CSV. 엑셀에서 한글이 깨지지 않도록 BOM 을 선두에 붙인다. */
    public static <T> byte[] toCsv(List<ExportColumn<T>> columns, List<T> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM — 엑셀에서 한글 CSV 가 깨지지 않게 선두에 붙인다.
        appendCsvRow(sb, columns.stream().map(ExportColumn::header).toList());
        for (T row : rows) {
            appendCsvRow(sb, columns.stream().map(column -> formatValue(column.extractor().apply(row))).toList());
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** POI SXSSF 스트리밍 xlsx. 숫자는 숫자 셀, 나머지는 문자열 셀로 기록한다. */
    public static <T> byte[] toExcel(String sheetName, List<ExportColumn<T>> columns, List<T> rows) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(EXCEL_WINDOW_SIZE);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (T row : rows) {
                Row sheetRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = columns.get(i).extractor().apply(row);
                    Cell cell = sheetRow.createCell(i);
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(formatValue(value));
                    }
                }
            }

            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Excel 내보내기 생성에 실패했습니다.", e);
        }
    }

    private static void appendCsvRow(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeCsv(values.get(i)));
        }
        sb.append("\r\n");
    }

    /** 콤마/따옴표/개행 포함 시에만 따옴표로 감싸고 내부 따옴표는 이중화한다(RFC4180). */
    private static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "Y" : "N";
        }
        return String.valueOf(value);
    }

    /** POI 시트명 금지 문자를 제거하고 31자로 자른다. */
    private static String safeSheetName(String name) {
        String cleaned = (name == null || name.isBlank() ? "export" : name)
                .replaceAll("[\\\\/*?:\\[\\]]", "_");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
