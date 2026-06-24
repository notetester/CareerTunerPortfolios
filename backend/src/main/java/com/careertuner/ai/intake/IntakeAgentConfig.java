package com.careertuner.ai.intake;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 인테이크 에이전트 빈 빌더. 커뮤니티의 {@code CommunityAgentConfig} 를 복제하되 다음을 분리한다.
 *
 * <ul>
 *   <li><b>전용 메모리 윈도우(maxMessages=40)</b>: 되묻기로 길어질 수 있어 별도 빈으로 신설한다.
 *       커뮤니티의 공유 {@code chatMemoryProvider}(20)는 무수정. 빈 이름이 다르므로(이름 매칭 폴백)
 *       커뮤니티 주입(파라미터명 {@code chatMemoryProvider})은 그대로 20 빈으로 해소된다.</li>
 *   <li>자동구성된 {@code ChatModel}(qwen3:8b)과 단일 {@code ChatMemoryStore}(MyBatis)는 타입으로 재사용.</li>
 *   <li>툴캡 {@code maxSequentialToolsInvocations}=3 으로 로컬 추론 폭주를 방지(커뮤니티와 동일).</li>
 * </ul>
 */
@Configuration
public class IntakeAgentConfig {

    private static final int MAX_TOOL_CALLS = 3;
    private static final int MAX_MESSAGES = 40;

    @Bean
    public ChatMemoryProvider intakeChatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    @Bean
    public IntakeChatAgent intakeChatAgent(ChatModel chatModel,
                                           @Qualifier("intakeChatMemoryProvider") ChatMemoryProvider intakeChatMemoryProvider,
                                           IntakeTools intakeTools) {
        return AiServices.builder(IntakeChatAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(intakeChatMemoryProvider)
                .tools(intakeTools)
                .maxSequentialToolsInvocations(MAX_TOOL_CALLS)
                .build();
    }
}
