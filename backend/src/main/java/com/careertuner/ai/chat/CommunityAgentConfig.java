package com.careertuner.ai.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

/**
 * 커뮤니티 챗봇 에이전트 빈 구성.
 * <p>@AiService 자동등록 대신 AiServices.builder 로 직접 만들어 <b>가드레일(tool-call 캡)</b>을 건다.
 * ChatModel(ollama 스타터)·ChatMemoryProvider(ChatMemoryConfig)·CommunityTools 는 컨테이너에서 주입.
 */
@Configuration
public class CommunityAgentConfig {

    /** 한 응답에서 허용하는 연속 tool 호출 상한 — 로컬 추론 루프/폭주 차단. */
    private static final int MAX_TOOL_CALLS = 3;

    @Bean
    public CommunityChatAgent communityChatAgent(ChatModel chatModel,
                                                 ChatMemoryProvider chatMemoryProvider,
                                                 CommunityTools communityTools) {
        return AiServices.builder(CommunityChatAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(communityTools)
                .maxSequentialToolsInvocations(MAX_TOOL_CALLS)
                .build();
    }

    /** quickReplies 보조 생성기 — 툴/메모리 없음(구조화 출력 충돌 회피). */
    @Bean
    public QuickReplyAgent quickReplyAgent(ChatModel chatModel) {
        return AiServices.builder(QuickReplyAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /** 추천 후기 묶음 요약 생성기 — 툴/메모리 없음(단발 입력→평문 요약). */
    @Bean
    public SummaryAgent summaryAgent(ChatModel chatModel) {
        return AiServices.builder(SummaryAgent.class)
                .chatModel(chatModel)
                .build();
    }
}
