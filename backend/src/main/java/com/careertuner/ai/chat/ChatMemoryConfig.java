package com.careertuner.ai.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;

/**
 * 챗봇 에이전트 메모리 설정. @AiService(@MemoryId) 가 이 ChatMemoryProvider 를 자동으로 사용한다.
 * conversationId 별로 최근 N개 메시지 윈도우를 MySQL 스토어 위에 얹는다.
 */
@Configuration
public class ChatMemoryConfig {

    /** LLM 입력에 실리는 최근 메시지 윈도우 크기 (UI 전체 이력과 별개). */
    private static final int MAX_MESSAGES = 20;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
