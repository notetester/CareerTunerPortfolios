package com.careertuner.ai.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

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
public final class QuickReplyParser {

    private QuickReplyParser() {}

    // FAIL_ON_TRAILING_TOKENS: "배열 하나만" strict. 칩마다 배열 따로(다배열)면 strict 실패 → 안전망이 객체 단위로 복구.
    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern CHIPS_BLOCK = Pattern.compile("```chips\\s*(.*?)```", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL);

    /** 선별: 1등 score 의 이 비율 미만 칩은 버린다(상대 가변 컷). */
    private static final double RELATIVE_CUT = 0.60;

    /**
     * raw 모델 출력에서 칩 후보를 견고하게 추출한다. 절대 throw 하지 않는다.
     */
    public static List<ChipSuggestion> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        // 1) strict: chips 블록에서 배열 하나
        Matcher m = CHIPS_BLOCK.matcher(raw);
        if (m.find()) {
            try {
                List<Map<String, Object>> arr =
                        MAPPER.readValue(m.group(1).trim(), new TypeReference<List<Map<String, Object>>>() {});
                List<ChipSuggestion> chips = normalize(arr);
                if (!chips.isEmpty()) {
                    return chips;
                }
            } catch (Exception ignore) {
                // strict 실패 → 안전망으로
            }
        }
        // 2) 안전망: 본문 어디서든 {...} 객체를 개별로
        List<Map<String, Object>> objs = new ArrayList<>();
        Matcher om = JSON_OBJECT.matcher(raw);
        while (om.find()) {
            try {
                objs.add(MAPPER.readValue(om.group(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignore) {
                // 깨진 조각은 건너뜀
            }
        }
        return normalize(objs);
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

    private static List<ChipSuggestion> normalize(List<Map<String, Object>> arr) {
        if (arr == null) {
            return List.of();
        }
        List<ChipSuggestion> out = new ArrayList<>();
        for (Map<String, Object> c : arr) {
            if (c == null) {
                continue;
            }
            Object textObj = c.get("text");
            if (textObj == null) textObj = c.get("chip");
            if (textObj == null) textObj = c.get("label");
            if (textObj == null) {
                continue; // text 없는 객체 드롭
            }
            String text = String.valueOf(textObj).trim();
            if (text.isEmpty()) {
                continue;
            }
            out.add(new ChipSuggestion(text, toInt(c.get("relevance")), toInt(c.get("importance"))));
        }
        return out;
    }

    /** "85"·85·85.0 모두 흡수, 실패 시 0. */
    private static int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
