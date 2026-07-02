package com.careertuner.community.moderation.client;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 검열/태그/추출 LLM 폴백 디스패처: 자체 Ollama(gemma4) → Claude(Haiku) → OpenAI → Mock.
 *
 * <p>{@code PostModerationService} 는 이 게이트웨이만 주입받아 provider 를 모른다. {@link OllamaClient} 와
 * 동일한 시그니처(chat(system, user, schema):String)라 호출부는 필드/이름만 바꾸면 된다.
 * 검열은 비동기 이벤트 처리라, 자체 모델이 죽어도 Haiku/OpenAI 로 판정을 이어가고 그마저 안 되면
 * Mock 이 "정상 통과 + 관리자 검토" 기본값을 돌려 파이프라인이 멈추지 않는다.
 */
@Component
public class ModerationLlmGateway {

    private static final Logger log = LoggerFactory.getLogger(ModerationLlmGateway.class);

    private final OllamaClient ollama;
    private final ModerationAnthropicClient anthropic;
    private final ModerationOpenAiClient openAi;
    private final MockModerationClient mock;

    public ModerationLlmGateway(OllamaClient ollama,
                                ModerationAnthropicClient anthropic,
                                ModerationOpenAiClient openAi,
                                MockModerationClient mock) {
        this.ollama = ollama;
        this.anthropic = anthropic;
        this.openAi = openAi;
        this.mock = mock;
    }

    public String chat(String systemPrompt, String userText, Map<String, Object> jsonSchema) {
        // 1) 자체 Ollama(gemma4) 우선 — OllamaClient 내부 재시도(최대 3회) 포함.
        try {
            return ollama.chat(systemPrompt, userText, jsonSchema);
        } catch (RuntimeException ex) {
            log.warn("검열 Ollama 호출 실패 → Claude 폴백: {}", ex.getMessage());
        }
        // 2) 1차 폴백: Claude(Haiku) — 공통 키라 가장 안정적.
        if (anthropic.available()) {
            try {
                return anthropic.chat(systemPrompt, userText, jsonSchema);
            } catch (RuntimeException ex) {
                log.warn("검열 Claude 호출 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        // 3) 2차 폴백: OpenAI.
        if (openAi.available()) {
            try {
                return openAi.chat(systemPrompt, userText, jsonSchema);
            } catch (RuntimeException ex) {
                log.warn("검열 OpenAI 호출 실패 → Mock 폴백: {}", ex.getMessage());
            }
        }
        // 4) 최종 폴백: Mock — 외부 provider 가 모두 미설정/실패해도 검열 파이프라인이 멈추지 않게 안전 기본값 반환.
        log.warn("검열 모든 LLM provider 미설정/실패 → Mock(정상 통과) 응답");
        return mock.chat(systemPrompt, userText, jsonSchema);
    }
}
