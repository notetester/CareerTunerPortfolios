package com.careertuner.support.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * #2 contains 의도 오탐 수정 검증. isExitCommand/isAffirmative 가 부분문자열이 아니라
 * 정확일치(+허용접미사)로만 매칭하는지 박는다. Ollama·DB 불필요(순수 매칭 로직).
 */
class ChatbotIntentMatchTest {

    // ── isExitCommand: 오탐 차단(이탈 아님) ─────────────────────────────
    @DisplayName("이탈 오탐 차단 — 키워드가 답변에 박혀도 이탈 아님")
    @ParameterizedTest(name = "[{index}] \"{0}\" → 이탈 아님")
    @ValueSource(strings = {
            "중단된 프로젝트 경험",
            "종료된 공고로 준비",
            "환불 취소 절차",
            "그만두지 않을래요"
    })
    void exit_falsePositive_blocked(String input) {
        assertThat(ChatbotController.isExitCommand(input)).isFalse();
    }

    // ── isExitCommand: 정상 이탈(이탈 맞음) ─────────────────────────────
    @DisplayName("이탈 정상 — 정확일치/허용접미사는 이탈")
    @ParameterizedTest(name = "[{index}] \"{0}\" → 이탈")
    @ValueSource(strings = {
            "그만",          // ⏏ 버튼이 보내는 정확한 신호
            "그만할래",
            "취소",
            "그만요",
            "종료"
    })
    void exit_truePositive_pass(String input) {
        assertThat(ChatbotController.isExitCommand(input)).isTrue();
    }

    // ── isAffirmative: 오탐 차단(긍정 아님) ─────────────────────────────
    @DisplayName("긍정 오탐 차단 — 단음절이 문장에 박혀도 긍정 아님")
    @ParameterizedTest(name = "[{index}] \"{0}\" → 긍정 아님")
    @ValueSource(strings = {
            "네이버 백엔드 면접",
            "예전 거 말고요",
            "응답이 느려서요"
    })
    void affirmative_falsePositive_blocked(String input) {
        assertThat(ChatbotController.isAffirmative(input)).isFalse();
    }

    // ── isAffirmative: 정상 긍정(긍정 맞음) ─────────────────────────────
    @DisplayName("긍정 정상 — 화이트리스트 정확일치는 긍정")
    @ParameterizedTest(name = "[{index}] \"{0}\" → 긍정")
    @ValueSource(strings = {
            "네",
            "좋아요",
            "응",
            "시작"
    })
    void affirmative_truePositive_pass(String input) {
        assertThat(ChatbotController.isAffirmative(input)).isTrue();
    }
}
