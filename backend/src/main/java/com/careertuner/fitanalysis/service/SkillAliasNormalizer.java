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
    private static final Map<String, List<String>> BLOCKED_PHRASES_BY_CANONICAL = blockedPhrasesByCanonical();

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
        String normalizedKey = canonicalize(canonicalKey);
        List<String> aliases = ALIASES_BY_CANONICAL.getOrDefault(normalizedKey, List.of(normalizedKey));
        for (String alias : aliases) {
            if (containsAllowedMention(normalizedText, alias, normalizedKey)) {
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
        // 한글 전사 별칭(FP triage, reports/84) — 실제 공고/이력서에서 흔한 한글 표기가 라틴 canonical 과
        // 매칭되도록 한다. 전부 완전 표면형이라 substring FN 위험 없음(자바/리액트/스프링 계열은 아래 block 참조).
        alias(map, "스프링부트", "spring boot");
        alias(map, "스프링 부트", "spring boot");
        alias(map, "리액트", "react");
        alias(map, "리액트 네이티브", "react native");
        alias(map, "react native", "react native");
        alias(map, "파이썬", "python");
        alias(map, "python", "python");
        alias(map, "자바스크립트", "javascript");
        alias(map, "타입스크립트", "typescript");
        alias(map, "노드", "nodejs");
        alias(map, "도커", "docker");
        alias(map, "docker", "docker");
        alias(map, "쿠버네티스", "kubernetes");
        alias(map, "코틀린", "kotlin");
        alias(map, "kotlin", "kotlin");
        alias(map, "장고", "django");
        alias(map, "django", "django");
        alias(map, "포토샵", "photoshop");
        alias(map, "포토샵 cc", "photoshop");
        alias(map, "photoshop cc", "photoshop");
        alias(map, "photoshop", "photoshop");
        alias(map, "일러스트레이터", "illustrator");
        alias(map, "illustrator", "illustrator");
        alias(map, "엑셀", "excel");
        alias(map, "excel", "excel");
        alias(map, "정처기", "정보처리기사");
        alias(map, "정보처리기사", "정보처리기사");
        alias(map, "컴활 1급", "컴퓨터활용능력 1급");
        alias(map, "컴퓨터활용능력 1급", "컴퓨터활용능력 1급");
        alias(map, "컴활 2급", "컴퓨터활용능력 2급");
        alias(map, "컴퓨터활용능력 2급", "컴퓨터활용능력 2급");
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

    private static Map<String, List<String>> blockedPhrasesByCanonical() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        block(map, "java", "javascript");
        block(map, "node", "node.js", "nodejs");
        block(map, "react", "react native");
        block(map, "spring", "spring boot");
        block(map, "sql", "mysql", "mssql", "postgresql", "postgres");
        // 한글 전사 표면형에도 동일한 상위표현 차단(자바→자바스크립트 등 confusion pair FN 방지).
        block(map, "자바", "자바스크립트");
        block(map, "리액트", "리액트 네이티브");
        block(map, "스프링", "스프링부트", "스프링 부트");
        block(map, "노드", "노드제이에스");
        block(map, "일러스트", "일러스트레이터");

        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            out.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static void alias(Map<String, String> map, String alias, String canonical) {
        map.put(normalize(alias), normalize(canonical));
    }

    private static void block(Map<String, List<String>> map, String canonical, String... phrases) {
        String normalizedCanonical = normalize(canonical);
        for (String phrase : phrases) {
            map.computeIfAbsent(normalizedCanonical, ignored -> new ArrayList<>()).add(normalize(phrase));
        }
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

    private static boolean containsAllowedMention(String text, String alias, String canonicalKey) {
        if (alias.isBlank()) {
            return false;
        }
        int index = text.indexOf(alias);
        while (index >= 0) {
            int end = index + alias.length();
            if (hasAsciiBoundary(text, index, end)
                    && !isDottedShortAliasSuffix(text, alias, index)
                    && !isBlockedCompoundMention(text, canonicalKey, index, end)) {
                return true;
            }
            index = text.indexOf(alias, index + 1);
        }
        return false;
    }

    private static boolean isDottedShortAliasSuffix(String text, String alias, int start) {
        return alias.length() <= 2 && start > 0 && text.charAt(start - 1) == '.';
    }

    private static boolean isBlockedCompoundMention(String text, String canonicalKey, int start, int end) {
        for (String blockedPhrase : BLOCKED_PHRASES_BY_CANONICAL.getOrDefault(canonicalKey, List.of())) {
            int phraseStart = text.indexOf(blockedPhrase);
            while (phraseStart >= 0) {
                int phraseEnd = phraseStart + blockedPhrase.length();
                if (phraseStart <= start && phraseEnd >= end && hasAsciiBoundary(text, phraseStart, phraseEnd)) {
                    return true;
                }
                phraseStart = text.indexOf(blockedPhrase, phraseStart + 1);
            }
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
