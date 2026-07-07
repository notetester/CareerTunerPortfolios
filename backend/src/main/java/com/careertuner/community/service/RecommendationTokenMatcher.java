package com.careertuner.community.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 추천 알림 토큰 매칭 — 룰 기반·설명 가능한 순수 함수 모음.
 *
 * <p>매칭 규칙(단순 부분일치):
 * 사용자 프로필의 희망 직무(desired_job)·스킬(skills)을 토큰으로 쪼갠 뒤,
 * 그 토큰 중 하나라도 글 텍스트(제목+태그+회사명+직무명)에 부분 문자열로 포함되면 매칭이다.
 * 예) 희망 직무 "백엔드 개발자" → 토큰 [백엔드, 개발자] → 제목 "네이버 백엔드 면접 후기" 매칭.
 *
 * <p>DB·Spring 의존이 없어 단위 테스트가 그대로 가능하다.
 */
public final class RecommendationTokenMatcher {

    /** 토큰 구분자 — 한글/영문/숫자, 그리고 기술명 표기(+, #, .)만 토큰 문자로 남긴다. (예: c++, c#, .net) */
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}+#.]+");

    /** 한 글자 토큰은 우연 일치가 많아 제외한다. */
    private static final int MIN_TOKEN_LENGTH = 2;

    private RecommendationTokenMatcher() {
    }

    /** 소문자 정규화. null 은 빈 문자열로 취급한다. */
    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).strip();
    }

    /**
     * 텍스트를 소문자 토큰 집합으로 쪼갠다.
     * 공백·구분 기호 기준 분리 후 {@value MIN_TOKEN_LENGTH}자 미만은 버린다(순서 유지·중복 제거).
     */
    public static List<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : TOKEN_SPLITTER.split(normalized)) {
            // 앞뒤에 붙은 구두점(예: "개발자.") 제거 — 단, "c++", "c#" 같은 기술명 표기는 보존한다
            String token = raw.strip();
            while (token.length() > 1 && token.endsWith(".")) {
                token = token.substring(0, token.length() - 1);
            }
            if (token.length() >= MIN_TOKEN_LENGTH) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * 프로필 매칭 재료(희망 직무 + 스킬 목록)를 하나의 토큰 목록으로 만든다.
     * 스킬은 이미 항목 단위이므로 항목별로 다시 토큰화해 합친다.
     */
    public static List<String> profileTokens(String desiredJob, Collection<String> skills) {
        List<String> tokens = new ArrayList<>(tokenize(desiredJob));
        if (skills != null) {
            for (String skill : skills) {
                for (String token : tokenize(skill)) {
                    if (!tokens.contains(token)) {
                        tokens.add(token);
                    }
                }
            }
        }
        return tokens;
    }

    /**
     * 프로필 토큰 중 하나라도 글 텍스트에 부분 문자열로 포함되면 true.
     *
     * @param postText      글 텍스트(제목+태그+회사명+직무명 연결). {@link #normalize} 전이어도 된다.
     * @param profileTokens {@link #profileTokens} 결과 (이미 소문자 정규화된 토큰)
     */
    public static boolean matchesAnyToken(String postText, Collection<String> profileTokens) {
        if (profileTokens == null || profileTokens.isEmpty()) {
            return false;
        }
        String normalizedText = normalize(postText);
        if (normalizedText.isEmpty()) {
            return false;
        }
        for (String token : profileTokens) {
            if (token != null && token.length() >= MIN_TOKEN_LENGTH && normalizedText.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
