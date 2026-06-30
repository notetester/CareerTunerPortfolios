package com.careertuner.fitanalysis.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C fitanalysis evidence gate 전용 skill alias normalizer.
 *
 * <p>사용자 원본/AI 응답 문자열을 바꾸지 않고, gate 내부 비교용 canonical key 만 만든다.
 * substring/fuzzy matching 은 하지 않으며, 명시 alias map 에 등록된 값만 canonical skill 로 치환한다.
 */
public final class SkillAliasNormalizer {

    private static final Map<String, String> ALIASES = aliases();
    private static final Map<String, List<String>> ALIASES_BY_CANONICAL = aliasesByCanonical();

    public String canonicalize(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        return ALIASES.getOrDefault(normalized, normalized);
    }

    public boolean containsCanonicalMention(String text, String canonicalKey) {
        String normalizedText = normalize(text);
        if (normalizedText.isBlank() || canonicalKey == null || canonicalKey.isBlank()) {
            return false;
        }
        List<String> aliases = ALIASES_BY_CANONICAL.getOrDefault(canonicalKey, List.of(canonicalKey));
        for (String alias : aliases) {
            if (containsWithAsciiBoundary(normalizedText, alias)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> aliases() {
        Map<String, String> map = new LinkedHashMap<>();
        alias(map, "apache spark", "spark");
        alias(map, "spark", "spark");

        alias(map, "postgres", "postgresql");
        alias(map, "postgresql", "postgresql");

        alias(map, "k8s", "kubernetes");
        alias(map, "kubernetes", "kubernetes");

        alias(map, "js", "javascript");
        alias(map, "javascript", "javascript");

        alias(map, "ts", "typescript");
        alias(map, "typescript", "typescript");

        alias(map, "node.js", "nodejs");
        alias(map, "nodejs", "nodejs");

        alias(map, "spring boot", "spring boot");
        alias(map, "springboot", "spring boot");

        alias(map, "react.js", "react");
        alias(map, "reactjs", "react");
        alias(map, "react", "react");

        alias(map, "vue.js", "vue");
        alias(map, "vuejs", "vue");
        alias(map, "vue", "vue");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, List<String>> aliasesByCanonical() {
        Map<String, List<String>> byCanonical = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            byCanonical.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(entry.getKey());
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : byCanonical.entrySet()) {
            out.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static void alias(Map<String, String> map, String alias, String canonical) {
        map.put(normalize(alias), normalize(canonical));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replaceAll("[\\u2010-\\u2015]", "-")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('/', ' ')
                .replace('-', ' ')
                .replaceAll("\\s*\\.\\s*", ".")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsWithAsciiBoundary(String text, String alias) {
        if (alias.isBlank()) {
            return false;
        }
        int index = text.indexOf(alias);
        while (index >= 0) {
            int end = index + alias.length();
            if (hasAsciiBoundary(text, index, end)) {
                return true;
            }
            index = text.indexOf(alias, index + 1);
        }
        return false;
    }

    private static boolean hasAsciiBoundary(String text, int start, int end) {
        return (start == 0 || !isAsciiLetterOrDigit(text.charAt(start - 1)))
                && (end == text.length() || !isAsciiLetterOrDigit(text.charAt(end)));
    }

    private static boolean isAsciiLetterOrDigit(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
    }
}
