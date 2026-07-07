package com.careertuner.community.moderation.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.careertuner.applicationcase.service.OpenAiProperties;
import com.careertuner.community.moderation.config.OllamaProperties;
import com.careertuner.interview.service.AnthropicProperties;

/**
 * 커뮤니티 검열/태그/추출 LLM 폴백 디스패처: 자체 Ollama(gemma4) → Claude(Haiku) → OpenAI → Mock.
 *
 * <p>{@code PostModerationService} 는 이 게이트웨이만 주입받아 provider 를 모른다.
 * 검열은 비동기 이벤트 처리라, 자체 모델이 죽어도 Haiku/OpenAI 로 판정을 이어가고 그마저 안 되면
 * Mock 이 미판정 placeholder 를 돌려 파이프라인이 멈추지 않는다.
 *
 * <p>응답({@link LlmReply})에는 <b>실제 응답한 provider 의 모델명</b>과 mock 여부를 싣는다 —
 * 호출부가 결과 저장 시 폴백 여부와 무관하게 Ollama 모델명을 기록하던 문제(M-02)와,
 * Mock placeholder 를 COMPLETED 로 확정하던 문제(M-01)를 이 정보로 분리한다.
 */
@Component
public class ModerationLlmGateway {

    private static final Logger log = LoggerFactory.getLogger(ModerationLlmGateway.class);

    /** Mock 폴백 시 결과 테이블 model 컬럼에 기록되는 식별자. */
    public static final String MOCK_MODEL = "mock";

    /** provider 응답 JSON + 실제 응답한 모델명. mock=true 면 판정이 아니라 미판정 placeholder 다. */
    public record LlmReply(String json, String model, boolean mock) {}

    private final OllamaClient ollama;
    private final ModerationAnthropicClient anthropic;
    private final ModerationOpenAiClient openAi;
    private final MockModerationClient mock;
    private final OllamaProperties ollamaProperties;
    private final AnthropicProperties anthropicProperties;
    private final OpenAiProperties openAiProperties;

    public ModerationLlmGateway(OllamaClient ollama,
                                ModerationAnthropicClient anthropic,
                                ModerationOpenAiClient openAi,
                                MockModerationClient mock,
                                OllamaProperties ollamaProperties,
                                AnthropicProperties anthropicProperties,
                                OpenAiProperties openAiProperties) {
        this.ollama = ollama;
        this.anthropic = anthropic;
        this.openAi = openAi;
        this.mock = mock;
        this.ollamaProperties = ollamaProperties;
        this.anthropicProperties = anthropicProperties;
        this.openAiProperties = openAiProperties;
    }

    public LlmReply chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        // 1) 자체 Ollama(gemma4) 우선 — OllamaClient 내부 재시도(최대 3회) 포함.
        try {
            return new LlmReply(ollama.chat(systemPrompt, userText, jsonSchema),
                    ollamaProperties.getModel(), false);
        } catch (RuntimeException ex) {
            log.warn("검열 Ollama 호출 실패 → Claude 폴백: {}", ex.getMessage());
        }
        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적.
        if (anthropic.available()) {
            try {
                return new LlmReply(anthropic.chat(systemPrompt, userText, jsonSchema),
                        anthropicProperties.getModel(), false);
            } catch (RuntimeException ex) {
                log.warn("검열 Claude 호출 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        // 3) 2차 폴백: OpenAI.
        if (openAi.available()) {
            try {
                return new LlmReply(openAi.chat(systemPrompt, userText, jsonSchema),
                        openAiProperties.getModel(), false);
            } catch (RuntimeException ex) {
                log.warn("검열 OpenAI 호출 실패 → Mock 폴백: {}", ex.getMessage());
            }
        }
        // 4) 최종 폴백: Mock — 외부 provider 가 모두 미설정/실패해도 파이프라인이 멈추지 않게
        //    미판정 placeholder 반환. 호출부는 mock=true 를 보고 UNMODERATED 로 기록한다.
        log.warn("검열 모든 LLM provider 미설정/실패 → Mock(미판정 placeholder) 응답");
        return new LlmReply(mock.chat(systemPrompt, userText, jsonSchema), MOCK_MODEL, true);
    }
}
