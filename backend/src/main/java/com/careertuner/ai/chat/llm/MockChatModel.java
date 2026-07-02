package com.careertuner.ai.chat.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * 챗봇 최종 폴백 ChatModel. 자체 Ollama·Claude·OpenAI 가 모두 미설정이거나 실패했을 때
 * {@link FallbackChatModel} 이 마지막으로 호출한다.
 *
 * <p>커뮤니티/인테이크 챗봇은 자유형 String 응답이라 스키마 더미가 불필요하다 — 고정 안내 문구
 * 한 줄만 {@link AiMessage} 로 반환해, 외부 AI 가 모두 끊겨도 챗봇 화면이 깨지지 않게 한다.
 * tool 을 호출하지 않는(=toolExecutionRequests 없는) AiMessage 라 AiServices 의 tool 루프가 즉시 종료된다.
 */
public class MockChatModel implements ChatModel {

    static final String MESSAGE =
            "지금은 AI 응답이 일시적으로 어려워요. 잠시 후 다시 시도해 주세요. "
            + "급하시면 커뮤니티 검색이나 자주 묻는 질문(FAQ)을 이용하실 수 있어요.";

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(MESSAGE))
                .build();
    }
}
