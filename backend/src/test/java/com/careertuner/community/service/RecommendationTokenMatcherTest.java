package com.careertuner.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 추천 알림 토큰 매칭(순수 함수) 검증.
 * DB/Spring 없이 {@link RecommendationTokenMatcher}만 직접 호출한다.
 */
class RecommendationTokenMatcherTest {

    @Test
    @DisplayName("희망 직무 토큰이 글 텍스트에 부분일치하면 매칭된다 (한글·대소문자 무시)")
    void matchesWhenDesiredJobTokenAppearsInPostText() {
        // 희망 직무 "백엔드 개발자" → 토큰 [백엔드, 개발자]
        List<String> tokens = RecommendationTokenMatcher.profileTokens("백엔드 개발자", null);
        assertThat(tokens).containsExactly("백엔드", "개발자");

        // 제목에 "백엔드"가 포함 → 매칭
        assertThat(RecommendationTokenMatcher.matchesAnyToken(
                "네이버 백엔드 신입 면접 후기 Spring 네이버", tokens)).isTrue();

        // 영문 토큰은 대소문자 무시 매칭
        List<String> engTokens = RecommendationTokenMatcher.profileTokens("Backend Engineer", null);
        assertThat(RecommendationTokenMatcher.matchesAnyToken(
                "backend 직무 서류·면접 후기", engTokens)).isTrue();

        // 아무 토큰도 없으면 매칭 안 됨
        assertThat(RecommendationTokenMatcher.matchesAnyToken(
                "디자이너 포트폴리오 피드백 부탁드립니다", tokens)).isFalse();
    }

    @Test
    @DisplayName("스킬 토큰도 매칭 재료에 합쳐지고, 1글자 토큰·빈 값은 걸러진다")
    void skillsAreMergedAndShortTokensFiltered() {
        // 스킬 "React"가 글 태그(react)와 부분일치 → 매칭
        List<String> tokens = RecommendationTokenMatcher.profileTokens("프론트엔드", List.of("React", "TypeScript"));
        assertThat(tokens).containsExactly("프론트엔드", "react", "typescript");
        assertThat(RecommendationTokenMatcher.matchesAnyToken(
                "카카오 최종 합격 후기 react 코딩테스트", tokens)).isTrue();

        // 1글자 토큰은 우연 일치가 많아 제외 — "a b" 는 토큰이 남지 않는다
        assertThat(RecommendationTokenMatcher.tokenize("a b")).isEmpty();

        // 희망 직무·스킬이 모두 비면 매칭 불가 (알림 미발행)
        assertThat(RecommendationTokenMatcher.matchesAnyToken(
                "아무 글", RecommendationTokenMatcher.profileTokens(null, List.of()))).isFalse();

        // 글 텍스트가 비어도 매칭 불가
        assertThat(RecommendationTokenMatcher.matchesAnyToken("  ", tokens)).isFalse();

        // 기술명 표기 보존: "C++" 토큰이 그대로 매칭된다
        List<String> cppTokens = RecommendationTokenMatcher.profileTokens("C++ 개발자", null);
        assertThat(cppTokens).contains("c++");
        assertThat(RecommendationTokenMatcher.matchesAnyToken("삼성전자 c++ 직무 면접 후기", cppTokens)).isTrue();
    }
}
