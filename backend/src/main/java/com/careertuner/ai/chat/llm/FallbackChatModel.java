package com.careertuner.ai.chat.llm;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.careertuner.ai.common.model.AiProviderChain;
import com.careertuner.ai.common.model.AiProviderTier;
import com.careertuner.ai.common.model.RequestedAiModel;

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

    /** 챗봇 기본 tier 순서. CAREERTUNER=자체 Ollama, CLAUDE=Haiku, OPENAI=OpenAI. Mock 은 tier 밖 최종 안전망. */
    private static final List<AiProviderTier> DEFAULT_ORDER =
            List.of(AiProviderTier.CAREERTUNER, AiProviderTier.CLAUDE, AiProviderTier.OPENAI);

    /** 자동구성 원본(구체타입 주입 → 자기 자신이 @Primary ChatModel 이라 생기는 자기참조 회피). */
    private final OllamaChatModel ollama;
    /** 키 없으면 null (ChatModelFallbackConfig 가 @Bean null 반환 → ObjectProvider 로 흡수). */
    private final ChatModel anthropic;
    private final ChatModel openAi;
    /** 항상 존재하는 최종 안전망. */
    private final ChatModel mock;
    /** 요청 스코프 사용자 모델 선택(없으면 AUTO). */
    private final ChatModelSelectionTrace selectionTrace;

    public FallbackChatModel(OllamaChatModel ollama,
                             @Qualifier("anthropicChatModel") ObjectProvider<ChatModel> anthropic,
                             @Qualifier("openAiChatModel") ObjectProvider<ChatModel> openAi,
                             @Qualifier("mockChatModel") ChatModel mock,
                             ChatModelSelectionTrace selectionTrace) {
        this.ollama = ollama;
        this.anthropic = anthropic.getIfAvailable();
        this.openAi = openAi.getIfAvailable();
        this.mock = mock;
        this.selectionTrace = selectionTrace;
    }

    /**
     * 사용자 선택 모델 tier 부터 시작하는 폴백. {@code AUTO}(요청 스코프 미설정 포함)는 자체 Ollama → Claude →
     * OpenAI → Mock 현행 순서와 동일하고, 명시 선택은 그 tier 부터 시작하되 실패/미설정 시 하위 tier → Mock 안전망까지
     * 폴백해 화면이 깨지지 않는다.
     */
    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        RequestedAiModel requestedModel = selectionTrace.current();
        for (AiProviderTier tier : AiProviderChain.startingFrom(requestedModel, DEFAULT_ORDER)) {
            switch (tier) {
                case CAREERTUNER -> {
                    // 자체 Ollama(qwen3).
                    try {
                        return ollama.chat(chatRequest);
                    } catch (RuntimeException ex) {
                        log.warn("챗봇 Ollama 호출 실패 → 다음 폴백: {}", ex.getMessage());
                    }
                }
                case CLAUDE -> {
                    // Claude(Haiku) — 공통 키라 가장 안정적. 키 없으면 null 이라 건너뛴다.
                    if (anthropic != null) {
                        try {
                            return anthropic.chat(chatRequest);
                        } catch (RuntimeException ex) {
                            log.warn("챗봇 Claude 호출 실패 → 다음 폴백: {}", ex.getMessage());
                        }
                    }
                }
                case OPENAI -> {
                    if (openAi != null) {
                        try {
                            return openAi.chat(chatRequest);
                        } catch (RuntimeException ex) {
                            log.warn("챗봇 OpenAI 호출 실패 → Mock 폴백: {}", ex.getMessage());
                        }
                    }
                }
            }
        }
        // 최종 폴백: Mock — 외부 provider 가 모두 미설정/실패해도 화면이 깨지지 않게 안내 문구를 반환한다.
        log.warn("챗봇 모든 LLM provider 미설정/실패 → Mock 응답");
        return mock.chat(chatRequest);
    }
}
