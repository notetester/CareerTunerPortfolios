package com.careertuner.support.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 운영/지원 텍스트 AI 폴백 디스패처 검증. SupportAnthropicProperties 기본값은 apiKey 가 비어 있어
 * Claude 단계가 건너뛰어지므로, primary 실패 시 목업까지 안전하게 도달하는지(=안 터지는지) 확인한다.
 */
class SupportTextFallbackGeneratorTest {

    private final SupportTextFallbackGenerator generator =
            new SupportTextFallbackGenerator(new SupportAnthropicProperties());

    @Test
    void primary가_성공하면_primary결과를_반환한다() {
        String result = generator.generate("시스템", "입력", () -> "올라마 응답", "목업 안내");
        assertThat(result).isEqualTo("올라마 응답");
    }

    @Test
    void primary가_예외면_Claude미설정시_목업으로_폴백한다() {
        String result = generator.generate("시스템", "입력",
                () -> {
                    throw new IllegalStateException("Ollama down");
                },
                "목업 안내");
        assertThat(result).isEqualTo("목업 안내");
    }

    @Test
    void primary가_빈응답이면_Claude미설정시_목업으로_폴백한다() {
        String result = generator.generate("시스템", "입력", () -> "   ", "목업 안내");
        assertThat(result).isEqualTo("목업 안내");
    }
}
