package com.careertuner.admin.securityops.feed;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.careertuner.admin.securityops.feed.PolicyFeedModels.ParsedFeedRule;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 정책기관 피드(CSV/JSON) 파서.
 *
 * <p>JSON 배열 또는 CSV(헤더 자동감지·위치기반 폴백)를 읽어 차단 규칙 후보로 변환한다.
 * matchType 이 명시되지 않으면 값 패턴으로 자동 추론한다('/'→CIDR, '~'/범위→IP_RANGE,
 * 2글자→COUNTRY, 'AS?숫자'→ASN, IP 파싱 가능→IP). '#' 주석·빈 줄은 건너뛴다.
 * TripTogether {@code parsePolicyFeedRows}/{@code normalizeFeedMatchType} 를 이식했다.</p>
 */
@Component
@RequiredArgsConstructor
public class PolicyFeedParser {

    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");
    private static final Pattern ASN = Pattern.compile("^(?i)AS?\\d+$");
    private static final List<String> VALUE_KEYS =
            List.of("value", "targetvalue", "target", "ip", "cidr", "country", "asn", "address");

    private final ObjectMapper objectMapper;

    public List<ParsedFeedRule> parse(String content, String defaultAction) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String trimmed = content.trim();
        String action = defaultAction == null || defaultAction.isBlank() ? "BLOCK" : defaultAction.trim().toUpperCase(Locale.ROOT);
        boolean json = trimmed.startsWith("[") || trimmed.startsWith("{");
        return json ? parseJson(trimmed, action) : parseCsv(trimmed, action);
    }

    /** JSON API 로 직접 넘어온 행 목록을 검증·정규화한다(rawText 없이 rows 만 준 경우). */
    public List<ParsedFeedRule> parseRows(List<PolicyFeedModels.PolicyFeedRow> rows, String defaultAction) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        String action = defaultAction == null || defaultAction.isBlank() ? "BLOCK" : defaultAction.trim().toUpperCase(Locale.ROOT);
        List<ParsedFeedRule> out = new ArrayList<>();
        for (PolicyFeedModels.PolicyFeedRow row : rows) {
            if (row == null) {
                continue;
            }
            out.add(build(row.value(), row.matchType(), row.action() == null ? action : row.action(), row.reason()));
        }
        return out;
    }

    private List<ParsedFeedRule> parseJson(String content, String defaultAction) {
        List<ParsedFeedRule> out = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode array = root.isArray() ? root : (root.has("rules") ? root.get("rules") : null);
            if (array == null || !array.isArray()) {
                return out;
            }
            for (JsonNode node : array) {
                String value = firstText(node, VALUE_KEYS);
                String matchType = firstText(node, List.of("matchtype", "type", "kind"));
                String rowAction = firstText(node, List.of("action", "ruleaction"));
                String reason = firstText(node, List.of("reason", "description", "memo"));
                out.add(build(value, matchType, rowAction == null ? defaultAction : rowAction, reason));
            }
        } catch (Exception e) {
            out.add(ParsedFeedRule.invalid(null, "JSON 파싱 실패: " + e.getMessage()));
        }
        return out;
    }

    private List<ParsedFeedRule> parseCsv(String content, String defaultAction) {
        List<ParsedFeedRule> out = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        int[] colIdx = null; // [value, matchType, action, reason]
        boolean headerChecked = false;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cells = splitCsv(line);
            if (!headerChecked) {
                headerChecked = true;
                colIdx = detectHeader(cells);
                if (colIdx != null) {
                    continue; // 헤더 행은 소비
                }
            }
            String value;
            String matchType = null;
            String action = defaultAction;
            String reason = null;
            if (colIdx != null) {
                value = cell(cells, colIdx[0]);
                matchType = cell(cells, colIdx[1]);
                String a = cell(cells, colIdx[2]);
                if (a != null) {
                    action = a;
                }
                reason = cell(cells, colIdx[3]);
            } else {
                // 위치 기반: 값[, 조치[, 사유]]
                value = cell(cells, 0);
                if (cells.length > 1 && looksLikeAction(cells[1])) {
                    action = cells[1].trim().toUpperCase(Locale.ROOT);
                    reason = cell(cells, 2);
                } else {
                    reason = cell(cells, 1);
                }
            }
            out.add(build(value, matchType, action, reason));
        }
        return out;
    }

    /** 헤더 행이면 [value,matchType,action,reason] 컬럼 인덱스(없으면 -1)를 반환, 데이터 행이면 null. */
    private int[] detectHeader(String[] cells) {
        int value = -1;
        int matchType = -1;
        int action = -1;
        int reason = -1;
        boolean anyKeyword = false;
        for (int i = 0; i < cells.length; i++) {
            String c = cells[i] == null ? "" : cells[i].trim().toLowerCase(Locale.ROOT);
            if (VALUE_KEYS.contains(c) && value < 0) {
                value = i;
                anyKeyword = true;
            } else if ((c.equals("matchtype") || c.equals("type") || c.equals("kind")) && matchType < 0) {
                matchType = i;
                anyKeyword = true;
            } else if ((c.equals("action") || c.equals("ruleaction")) && action < 0) {
                action = i;
                anyKeyword = true;
            } else if ((c.equals("reason") || c.equals("description") || c.equals("memo")) && reason < 0) {
                reason = i;
                anyKeyword = true;
            }
        }
        if (!anyKeyword) {
            return null; // 헤더 아님 → 위치 기반 파싱
        }
        return new int[] {value < 0 ? 0 : value, matchType, action, reason};
    }

    private ParsedFeedRule build(String value, String matchType, String action, String reason) {
        String v = value == null ? null : value.trim();
        if (v == null || v.isEmpty()) {
            return ParsedFeedRule.invalid(value, "값이 비어 있음");
        }
        String type = (matchType == null || matchType.isBlank()) ? inferMatchType(v) : normalizeType(matchType);
        if (type == null) {
            return ParsedFeedRule.invalid(v, "매치 타입을 추론할 수 없음");
        }
        String error = validate(v, type);
        if (error != null) {
            return ParsedFeedRule.invalid(v, error);
        }
        String act = (action == null || action.isBlank()) ? "BLOCK" : action.trim().toUpperCase(Locale.ROOT);
        return new ParsedFeedRule(v, type, act, reason == null || reason.isBlank() ? null : reason.trim(), true, null);
    }

    String inferMatchType(String value) {
        String v = value.trim();
        if (v.contains("/")) {
            return "CIDR";
        }
        if (v.contains("~") || (v.contains("-") && isRangeLike(v))) {
            return "IP_RANGE";
        }
        if (ASN.matcher(v).matches()) {
            return "ASN";
        }
        if (COUNTRY.matcher(v).matches()) {
            return "COUNTRY";
        }
        if (isIp(v)) {
            return "IP";
        }
        return null;
    }

    private String normalizeType(String matchType) {
        String t = matchType.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "IP", "SINGLE_IP" -> "IP";
            case "CIDR" -> "CIDR";
            case "RANGE", "IP_RANGE" -> "IP_RANGE";
            case "COUNTRY", "GEO" -> "COUNTRY";
            case "ASN" -> "ASN";
            default -> null;
        };
    }

    private String validate(String value, String type) {
        return switch (type) {
            case "IP" -> isIp(value) ? null : "유효하지 않은 IP";
            case "CIDR" -> isCidr(value) ? null : "유효하지 않은 CIDR";
            case "IP_RANGE" -> isRangeLike(value) ? null : "유효하지 않은 IP 범위";
            case "COUNTRY" -> COUNTRY.matcher(value).matches() ? null : "국가 코드는 2글자여야 함";
            case "ASN" -> ASN.matcher(value).matches() ? null : "유효하지 않은 ASN";
            default -> "알 수 없는 타입";
        };
    }

    private boolean isRangeLike(String value) {
        String[] parts = value.contains("~") ? value.split("~") : value.split("-", 2);
        return parts.length == 2 && isIp(parts[0].trim()) && isIp(parts[1].trim());
    }

    private boolean isCidr(String value) {
        String[] parts = value.split("/");
        if (parts.length != 2) {
            return false;
        }
        try {
            int prefix = Integer.parseInt(parts[1].trim());
            InetAddress addr = InetAddress.getByName(parts[0].trim());
            int maxBits = addr.getAddress().length * 8;
            return prefix >= 0 && prefix <= maxBits;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isIp(String value) {
        try {
            String v = value.trim();
            // getByName 은 호스트명도 해석하므로 숫자/콜론/점만 허용해 IP 리터럴만 인정
            if (!v.matches("[0-9A-Fa-f:.]+")) {
                return false;
            }
            InetAddress.getByName(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean looksLikeAction(String s) {
        String v = s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        return v.equals("BLOCK") || v.equals("ALLOWLIST") || v.equals("ALLOW") || v.equals("REVIEW") || v.equals("CHALLENGE");
    }

    private String[] splitCsv(String line) {
        // 단순 CSV(따옴표 없는 값 전제) — 콤마 또는 탭 구분
        return line.contains("\t") && !line.contains(",") ? line.split("\t") : line.split(",");
    }

    private String cell(String[] cells, int idx) {
        if (idx < 0 || idx >= cells.length) {
            return null;
        }
        String c = cells[idx].trim();
        return c.isEmpty() ? null : c;
    }

    private String firstText(JsonNode node, List<String> keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            for (String actual : new String[] {key, key.toUpperCase(Locale.ROOT)}) {
                JsonNode field = node.get(actual);
                if (field != null && !field.isNull() && !field.asText().isBlank()) {
                    return field.asText().trim();
                }
            }
        }
        return null;
    }
}
