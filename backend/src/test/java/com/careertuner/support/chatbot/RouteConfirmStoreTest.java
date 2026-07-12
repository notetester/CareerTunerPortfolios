package com.careertuner.support.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RouteConfirmStoreTest {

    @Test
    void preservesOriginalQuestionForOneConfirmationTurn() {
        RouteConfirmStore store = new RouteConfirmStore();
        store.markPending(42L, "결제 취소는 어떻게 해?");

        assertThat(store.consumePendingQuestion(42L)).isEqualTo("결제 취소는 어떻게 해?");
        assertThat(store.consumePendingQuestion(42L)).isNull();
    }

    @Test
    void recognizesOnlyGenericQuestionConfirmationCopy() {
        assertThat(ChatbotController.isGenericQuestionConfirmation("그냥 질문이에요")).isTrue();
        assertThat(ChatbotController.isGenericQuestionConfirmation("아니요")).isTrue();
        assertThat(ChatbotController.isGenericQuestionConfirmation("아니요, 환불 질문이에요")).isFalse();
    }
}
