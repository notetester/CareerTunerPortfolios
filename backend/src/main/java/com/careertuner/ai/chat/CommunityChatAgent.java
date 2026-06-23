package com.careertuner.ai.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 커뮤니티 챗봇 에이전트. 검색을 강제하지 않고, 모델이 {@link CommunityTools} 를 스스로 판단해 호출한다.
 * <p><b>String 반환</b>: 구조화 POJO 반환을 강제하면(JSON 지시) qwen3:8b 가 tool_call 을 건너뛰고
 * 즉시 JSON 만 뱉는 충돌이 있어(실측 확인), 게이트에서 검증된 String 반환으로 둔다.
 * links 는 모델 JSON 이 아니라 실제 툴 출력({@link SearchTrace})에서 접지하고,
 * quickReplies 는 별도 {@link QuickReplyAgent} 가 만든다.
 * 빈은 {@code CommunityAgentConfig} 에서 AiServices.builder 로 직접 만든다(tool-call 캡 적용).
 */
public interface CommunityChatAgent {

    @SystemMessage(fromResource = "prompts/community-chat-system.txt")
    String chat(@MemoryId Long conversationId, @UserMessage String message);
}
