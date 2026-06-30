package com.careertuner.ai.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeType;

/**
 * quickReply 모델 raw 출력 → {@link ChipSuggestion} 견고 파서 + 선별.
 *
 * <p>8B 는 포맷을 자주 흘린다(칩마다 배열 따로, 점수 문자열 등). 칩은 선택 UI 보조라
 * <b>절대 throw 하지 않고</b>, 못 건지면 빈 리스트를 돌려준다(호출부 graceful degradation).
 *
 * <p>파싱 전략(chip_ab.py 검증 로직 포팅):
 * <ol>
 *   <li><b>strict</b>: {@code ```chips} 코드블록에서 JSON 배열 하나를 파싱.</li>
 *   <li><b>안전망</b>: 실패하면 본문 어디서든 {@code {...}} 객체를 개별로 긁어 모음.</li>
 * </ol>
 * normalize: {@code text|chip|label} 중 하나라도 있으면 칩, 점수 문자열("85")은 정수로 코어션,
 * text 없는 객체는 드롭.
 */
@Component
public final class QuickReplyParser {

    private final ObjectMapper objectMapper;

    private static final Pattern CHIPS_BLOCK = Pattern.compile("```chips\\s*(.*?)```", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL);

    /** 선별: 1등 score 의 이 비율 미만 칩은 버린다(상대 가변 컷). */
    private static final double RELATIVE_CUT = 0.60;

    public QuickReplyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * raw 모델 출력에서 칩 후보를 견고하게 추출한다. 절대 throw 하지 않는다.
     */
    public List<ChipSuggestion> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        // 1) strict: chips 블록에서 배열 하나
        Matcher m = CHIPS_BLOCK.matcher(raw);
        if (m.find()) {
            try {
                List<ChipSuggestion> chips = normalizeArray(readSingleArray(m.group(1).trim()));
                if (!chips.isEmpty()) {
                    return chips;
                }
            } catch (Exception ignore) {
                // strict 실패 → 안전망으로
            }
        }
        // 2) 안전망: 본문 어디서든 {...} 객체를 개별로
        List<JsonNode> objs = new ArrayList<>();
        Matcher om = JSON_OBJECT.matcher(raw);
        while (om.find()) {
            try {
                JsonNode node = objectMapper.readTree(om.group());
                if (node != null && node.isObject()) {
                    objs.add(node);
                }
            } catch (Exception ignore) {
                // 깨진 조각은 건너뜀
            }
        }
        return normalizeObjects(objs);
    }

    /**
     * score 내림차순 정렬 → 1등의 {@value #RELATIVE_CUT} 미만 컷 → 최대 {@code max} 개 → text 만 매핑.
     * (게이트 필터는 호출부에서 적용 후 넘긴다.)
     */
    public static List<String> select(List<ChipSuggestion> chips, int max) {
        if (chips == null || chips.isEmpty() || max <= 0) {
            return List.of();
        }
        List<ChipSuggestion> sorted = chips.stream()
                .sorted(Comparator.comparingDouble(ChipSuggestion::score).reversed())
                .collect(Collectors.toList());
        double cut = sorted.get(0).score() * RELATIVE_CUT;
        return sorted.stream()
                .filter(c -> c.score() >= cut)
                .limit(max)
                .map(ChipSuggestion::text)
                .collect(Collectors.toList());
    }

    private JsonNode readSingleArray(String json) throws JacksonException {
        int arrayEnd = findArrayEnd(json);
        if (arrayEnd < 0 || !json.substring(arrayEnd + 1).trim().isEmpty()) {
            throw new IllegalArgumentException("chips 블록은 JSON 배열 하나만 허용합니다");
        }
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("chips 블록은 JSON 배열이어야 합니다");
        }
        return root;
    }

    private static int findArrayEnd(String json) {
        if (json == null || json.isBlank() || json.charAt(0) != '[') {
            return -1;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
                if (depth < 0) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static List<ChipSuggestion> normalizeArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<JsonNode> objects = new ArrayList<>();
        for (JsonNode node : arr) {
            if (node != null && node.isObject()) {
                objects.add(node);
            }
        }
        return normalizeObjects(objects);
    }

    private static List<ChipSuggestion> normalizeObjects(List<JsonNode> arr) {
        List<ChipSuggestion> out = new ArrayList<>();
        for (JsonNode c : arr) {
            if (c == null) {
                continue;
            }
            JsonNode textNode = c.path("text");
            if (textNode.isMissingNode()) textNode = c.path("chip");
            if (textNode.isMissingNode()) textNode = c.path("label");
            if (textNode.isMissingNode()) {
                continue; // text 없는 객체 드롭
            }
            String text = textNode.asString("").trim();
            if (text.isEmpty()) {
                continue;
            }
            out.add(new ChipSuggestion(text, toInt(c.path("relevance")), toInt(c.path("importance"))));
        }
        return out;
    }

    /** "85"·85·85.0 모두 흡수, 실패 시 0. */
    private static int toInt(JsonNode v) {
        if (v == null || v.isMissingNode() || v.getNodeType() == JsonNodeType.NULL) {
            return 0;
        }
        if (v.isIntegralNumber() || v.isFloatingPointNumber()) {
            return v.intValue();
        }
        try {
            return (int) Double.parseDouble(v.asString("").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
