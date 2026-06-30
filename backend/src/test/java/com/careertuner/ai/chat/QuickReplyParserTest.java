package com.careertuner.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * quickReply 견고 파서/선별 검증. 8B 가 흘리는 포맷에서도 throw 없이 칩을 건지는지,
 * 선별이 점수 기반 가변 컷(절대 임계값 X)으로 동작하는지 확인한다.
 */
class QuickReplyParserTest {

    private final QuickReplyParser parser = new QuickReplyParser(new ObjectMapper());

    @Test
    @DisplayName("정상 단일 배열 — 점수까지 파싱")
    void parsesSingleArray() {
        String raw = """
                답변 평문입니다.
                ```chips
                [{"text":"강점 근거 찾기","relevance":90,"importance":80},
                 {"text":"STAR로 풀기","relevance":70,"importance":60}]
                ```""";
        List<ChipSuggestion> chips = parser.parse(raw);
        assertThat(chips).hasSize(2);
        assertThat(chips.get(0).text()).isEqualTo("강점 근거 찾기");
        assertThat(chips.get(0).relevance()).isEqualTo(90);
        assertThat(chips.get(0).importance()).isEqualTo(80);
    }

    @Test
    @DisplayName("칩마다 배열 따로(구버전 8B 버그) — 안전망이 객체 단위로 모두 복구")
    void recoversPerChipArrays() {
        String raw = """
                ```chips
                [{"text":"A","relevance":90,"importance":80}]
                [{"text":"B","relevance":80,"importance":70}]
                [{"text":"C","relevance":70,"importance":60}]
                ```""";
        List<ChipSuggestion> chips = parser.parse(raw);
        assertThat(chips).extracting(ChipSuggestion::text).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("점수가 문자열이면 정수로 코어션")
    void coercesStringScores() {
        String raw = """
                ```chips
                [{"text":"문자열점수","relevance":"85","importance":"70.0"}]
                ```""";
        List<ChipSuggestion> chips = parser.parse(raw);
        assertThat(chips).hasSize(1);
        assertThat(chips.get(0).relevance()).isEqualTo(85);
        assertThat(chips.get(0).importance()).isEqualTo(70);
    }

    @Test
    @DisplayName("text 없는 객체는 드롭, 점수 누락은 0")
    void dropsTextlessAndDefaultsScores() {
        String raw = """
                ```chips
                [{"relevance":90,"importance":80},
                 {"text":"점수없음"},
                 {"chip":"대체키도 허용"}]
                ```""";
        List<ChipSuggestion> chips = parser.parse(raw);
        assertThat(chips).extracting(ChipSuggestion::text).containsExactly("점수없음", "대체키도 허용");
        assertThat(chips.get(0).relevance()).isZero();
    }

    @Test
    @DisplayName("빈 배열 → 빈 리스트")
    void emptyArrayYieldsEmpty() {
        assertThat(parser.parse("```chips\n[]\n```")).isEmpty();
    }

    @Test
    @DisplayName("깨진 JSON / 블록 없음 / null → throw 없이 빈 리스트")
    void brokenInputNeverThrows() {
        assertThatCode(() -> {
            assertThat(parser.parse(null)).isEmpty();
            assertThat(parser.parse("")).isEmpty();
            assertThat(parser.parse("그냥 평문, 칩 없음")).isEmpty();
            assertThat(parser.parse("```chips\n[{\"text\":\"깨짐\", relevance:}]\n```")).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("블록 밖 본문에 객체가 흩어져도 긁어 모음")
    void scrapesObjectsOutsideBlock() {
        String raw = "여기 {\"text\":\"E\",\"relevance\":75,\"importance\":60} 저기도 {\"text\":\"F\",\"relevance\":50}";
        assertThat(parser.parse(raw)).extracting(ChipSuggestion::text).containsExactly("E", "F");
    }

    @Test
    @DisplayName("선별 — score 정렬 + 1등 60% 미만 컷 + 최대 3")
    void selectsByScoreWithRelativeCutAndCap() {
        List<ChipSuggestion> chips = List.of(
                new ChipSuggestion("top", 100, 100),   // score 100
                new ChipSuggestion("mid", 80, 80),      // score 80  (>= 60 컷)
                new ChipSuggestion("low", 50, 50),      // score 50  (< 60 컷 → 버림)
                new ChipSuggestion("hi2", 90, 90));     // score 90
        List<String> picked = QuickReplyParser.select(chips, 3);
        assertThat(picked).containsExactly("top", "hi2", "mid"); // 정렬 + low 컷 + 3캡
        assertThat(picked).doesNotContain("low");
    }

    @Test
    @DisplayName("선별 — 빈 입력/0개 max 는 빈 리스트")
    void selectEdgeCases() {
        assertThat(QuickReplyParser.select(List.of(), 3)).isEmpty();
        assertThat(QuickReplyParser.select(List.of(new ChipSuggestion("x", 90, 90)), 0)).isEmpty();
    }
}
