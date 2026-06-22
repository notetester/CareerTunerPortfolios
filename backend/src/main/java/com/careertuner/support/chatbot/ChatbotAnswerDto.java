package com.careertuner.support.chatbot;

import java.util.List;

public record ChatbotAnswerDto(
        String answer,
        List<SiteLink> links,
        List<Long> matchedFaqIds,
        double topSimilarity
) {
    private static final String NO_ANSWER = "해당 내용은 확인이 어렵습니다. 고객센터로 문의해 주시면 도와드리겠습니다.";

    public static ChatbotAnswerDto noMatch() {
        return new ChatbotAnswerDto(NO_ANSWER, List.of(), List.of(), 0.0);
    }
}
