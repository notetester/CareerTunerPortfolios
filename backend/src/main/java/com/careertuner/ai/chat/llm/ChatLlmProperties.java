package com.careertuner.ai.chat.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 챗봇 폴백 provider(Claude Haiku / OpenAI) 전용 튜닝값.
 *
 * <p>키·baseUrl·Anthropic 모델명은 기존 공용 설정(careertuner.anthropic / careertuner.openai)을
 * 그대로 재사용하고, 여기서는 챗봇 성격에 맞는 값(낮은 temperature, 짧은 출력, 비추론 OpenAI 모델)만 분리한다.
 * application.yaml 에 블록이 없어도 아래 기본값으로 동작한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "careertuner.chat-llm")
public class ChatLlmProperties {

    /** 라우팅/툴 판단 일관성을 위해 Ollama(0.0)와 동일하게 낮게 둔다. */
    private double temperature = 0.0;

    /** 챗봇 응답은 길 필요가 없어 넉넉하되 과하지 않게. */
    private int maxTokens = 4096;

    /**
     * OpenAI 폴백 모델. 공용 {@code careertuner.openai.model} 기본값(gpt-5)은 추론 모델이라
     * tool-calling 응답이 느려 실시간 챗봇에 부적합 → 비추론 모델로 override 한다.
     */
    private String openAiModel = "gpt-4o-mini";
}
