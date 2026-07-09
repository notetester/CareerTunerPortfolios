package com.careertuner.profile.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM 구조화 결과 결정적 후처리.
 * 1) 날짜 YYYY-MM 만 허용 2) school/company/title 원문 대조 3) 전 null 항목 제거 4) period 파생.
 */
public final class ProfileResumePostProcessor {

    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final List<String> EDUCATION_STATUS =
            List.of("졸업", "재학", "휴학", "중퇴", "수료", "졸업예정");

    private ProfileResumePostProcessor() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> processEducation(Object raw, String sourceText) {
        List<Map<String, Object>> rows = asMapList(raw);
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String school = str(row.get("school"));
            if (!appearsInSource(school, sourceText)) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("school", school);
            item.put("major", nullToEmpty(str(row.get("major"))));
            item.put("startDate", normalizeDate(str(row.get("startDate"))));
            item.put("endDate", normalizeDate(str(row.get("endDate"))));
            item.put("status", normalizeStatus(str(row.get("status"))));
            item.put("period", formatPeriod(item.get("startDate"), item.get("endDate")));
            if (isAllBlankExcept(item, "period")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> processCareer(Object raw, String sourceText) {
        List<Map<String, Object>> rows = asMapList(raw);
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String company = str(row.get("company"));
            if (!appearsInSource(company, sourceText)) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("company", company);
            item.put("role", nullToEmpty(str(row.get("role"))));
            item.put("startDate", normalizeDate(str(row.get("startDate"))));
            item.put("endDate", normalizeDate(str(row.get("endDate"))));
            item.put("tasks", nullToEmpty(str(row.get("tasks"))));
            item.put("achievements", nullToEmpty(str(row.get("achievements"))));
            item.put("period", formatPeriod(item.get("startDate"), item.get("endDate")));
            if (isAllBlankExcept(item, "period")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> processProjects(Object raw, String sourceText) {
        List<Map<String, Object>> rows = asMapList(raw);
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String title = str(row.get("title"));
            if (!appearsInSource(title, sourceText)) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("type", nullToEmpty(str(row.get("type"))));
            item.put("role", nullToEmpty(str(row.get("role"))));
            item.put("startDate", normalizeDate(str(row.get("startDate"))));
            item.put("endDate", normalizeDate(str(row.get("endDate"))));
            item.put("description", nullToEmpty(str(row.get("description"))));
            item.put("result", nullToEmpty(str(row.get("result"))));
            item.put("period", formatPeriod(item.get("startDate"), item.get("endDate")));
            if (isAllBlankExcept(item, "period")) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    /** Profile.tsx formatPeriod 복제. */
    public static String formatPeriod(String startDate, String endDate) {
        boolean hasStart = startDate != null && !startDate.isBlank();
        boolean hasEnd = endDate != null && !endDate.isBlank();
        if (hasStart && hasEnd) {
            return startDate + " - " + endDate;
        }
        if (hasStart) {
            return startDate + " - 현재";
        }
        if (hasEnd) {
            return endDate;
        }
        return "";
    }

    public static String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return YEAR_MONTH.matcher(trimmed).matches() ? trimmed : "";
    }

    /** 공백 제거 후 원문에 부분 문자열로 존재하는지. */
    public static boolean appearsInSource(String value, String sourceText) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        String needle = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        String hay = sourceText.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return !needle.isEmpty() && hay.contains(needle);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String trimmed = status.trim();
        for (String allowed : EDUCATION_STATUS) {
            if (allowed.equals(trimmed)) {
                return allowed;
            }
        }
        return "";
    }

    private static boolean isAllBlankExcept(Map<String, String> item, String... ignoreKeys) {
        for (Map.Entry<String, String> e : item.entrySet()) {
            boolean ignore = false;
            for (String k : ignoreKeys) {
                if (k.equals(e.getKey())) {
                    ignore = true;
                    break;
                }
            }
            if (ignore) {
                continue;
            }
            if (e.getValue() != null && !e.getValue().isBlank()) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        row.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                out.add(row);
            }
        }
        return out;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
