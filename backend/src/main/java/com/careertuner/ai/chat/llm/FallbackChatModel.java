package com.careertuner.ai.chat.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

/**
 * 챗봇 LLM 폴백 데코레이터: 자체 Ollama(qwen3) → Claude Haiku → OpenAI → Mock.
 *
 * <p>LangChain4j 의 @AiService(CommunityChatAgent·IntakeChatAgent·QuickReplyAgent·SummaryAgent)는
 * {@link ChatModel} 을 타입으로 주입받는다. 이 클래스가 유일한 @Primary ChatModel 이라 그 자리를 차지하고,
 * 원본 자동구성 {@link OllamaChatModel} 은 구체타입으로 1차 위임 대상으로 주입받는다.
 *
 * <p>AiServices 의 실제 호출 진입점은 {@link #chat(ChatRequest)} 하나뿐이라, 이것만 가로채면
 * tool-calling 을 포함한 모든 대화 호출을 잡는다. ChatRequest 에는 tool 스펙·메시지·파라미터가 요청 단위로
 * 담기므로, 그 객체를 그대로 하위 provider 에 위임하면 Haiku/OpenAI 폴백에서도 커뮤니티/인테이크 툴 호출이
 * 그대로 유지된다.
 *
 * <p>⚠️ 이 클래스가 프로젝트의 <b>유일한 @Primary ChatModel</b> 이어야 한다(다른 @Primary ChatModel 추가 금지 —
 * 주입 모호성 발생).
 */
@Primary
@Component
public class FallbackChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

    /** 자동구성 원본(구체타입 주입 → 자기 자신이 @Primary ChatModel 이라 생기는 자기참조 회피). */
    private final OllamaChatModel ollama;
    /** 키 없으면 null (ChatModelFallbackConfig 가 @Bean null 반환 → ObjectProvider 로 흡수). */
    private final ChatModel anthropic;
    private final ChatModel openAi;
    /** 항상 존재하는 최종 안전망. */
    private final ChatModel mock;

    public FallbackChatModel(OllamaChatModel ollama,
                             @Qualifier("anthropicChatModel") ObjectProvider<ChatModel> anthropic,
                             @Qualifier("openAiChatModel") ObjectProvider<ChatModel> openAi,
                             @Qualifier("mockChatModel") ChatModel mock) {
        this.ollama = ollama;
        this.anthropic = anthropic.getIfAvailable();
        this.openAi = openAi.getIfAvailable();
        this.mock = mock;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        // 1) 자체 Ollama(qwen3) 우선.
        try {
            return ollama.chat(chatRequest);
        } catch (RuntimeException ex) {
            log.warn("챗봇 Ollama 호출 실패 → Claude 폴백: {}", ex.getMessage());
        }
        // 2) 1차 폴백: Claude(Haiku) — run-local.bat 으로 주입되는 공통 키라 가장 안정적.
        if (anthropic != null) {
            try {
                return anthropic.chat(chatRequest);
            } catch (RuntimeException ex) {
                log.warn("챗봇 Claude 호출 실패 → OpenAI 폴백: {}", ex.getMessage());
            }
        }
        // 3) 2차 폴백: OpenAI.
        if (openAi != null) {
            try {
                return openAi.chat(chatRequest);
            } catch (RuntimeException ex) {
                log.warn("챗봇 OpenAI 호출 실패 → Mock 폴백: {}", ex.getMessage());
            }
        }
        // 4) 최종 폴백: Mock — 외부 provider 가 모두 미설정/실패해도 화면이 깨지지 않게 안내 문구를 반환한다.
        log.warn("챗봇 모든 LLM provider 미설정/실패 → Mock 응답");
        return mock.chat(chatRequest);
    }
}
