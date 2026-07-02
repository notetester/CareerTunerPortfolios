package com.careertuner.ai.chat.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.interview.service.AnthropicProperties;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * 챗봇 폴백 하위 provider(Anthropic/OpenAI/Mock) 빈 팩토리.
 *
 * <p>키·baseUrl·Anthropic 모델명은 기존 공용 설정({@link AnthropicProperties} careertuner.anthropic /
 * {@link OpenAiProperties} careertuner.openai)을 그대로 재사용한다. 챗봇 성격 튜닝값(temperature·maxTokens·
 * 비추론 OpenAI 모델)만 {@link ChatLlmProperties}(careertuner.chat-llm)로 분리한다.
 *
 * <p>키가 없으면 @Bean 이 null 을 반환해 빈을 등록하지 않고, {@link FallbackChatModel} 이 ObjectProvider 로
 * 그 부재를 흡수한다. (D 도메인의 {@code available()} 런타임 체크와 동일한 취지 — {@code @ConditionalOnProperty}
 * 는 {@code ${ANTHROPIC_API_KEY:}} 같은 빈 문자열 기본값을 "존재"로 오판하므로 쓰지 않는다.)
 */
@Configuration
public class ChatModelFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFallbackConfig.class);

    /** Claude(Haiku) 챗봇 폴백. 키 없으면 null(빈 미등록). */
    @Bean
    public ChatModel anthropicChatModel(AnthropicProperties props, ChatLlmProperties chatProps) {
        if (!props.configured()) {
            log.info("챗봇 폴백: ANTHROPIC_API_KEY 미설정 → Claude 폴백 비활성");
            return null;
        }
        return AnthropicChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .version(props.getVersion())
                .modelName(props.getModel())
                .maxTokens(chatProps.getMaxTokens())
                .temperature(chatProps.getTemperature())
                .build();
    }

    /** OpenAI 챗봇 폴백. 키 없으면 null(빈 미등록). 모델은 비추론(gpt-4o-mini 류)으로 override. */
    @Bean
    public ChatModel openAiChatModel(OpenAiProperties props, ChatLlmProperties chatProps) {
        if (!props.configured()) {
            log.info("챗봇 폴백: OPENAI_API_KEY 미설정 → OpenAI 폴백 비활성");
            return null;
        }
        return OpenAiChatModel.builder()
                .apiKey(props.getApiKey())
                .baseUrl(props.getBaseUrl())
                .modelName(chatProps.getOpenAiModel())
                .maxTokens(chatProps.getMaxTokens())
                .temperature(chatProps.getTemperature())
                .build();
    }

    /** 최종 안전망 — 항상 등록. */
    @Bean
    public ChatModel mockChatModel() {
        return new MockChatModel();
    }
}
