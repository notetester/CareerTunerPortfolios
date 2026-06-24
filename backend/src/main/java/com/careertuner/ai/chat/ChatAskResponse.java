package com.careertuner.ai.chat;

import java.util.List;

import com.careertuner.ai.chat.ChatResponse.SiteLink;

/**
 * 챗봇 에이전트 응답. conversationId 를 함께 내려 클라이언트가 다음 턴에 재사용한다.
 */
public record ChatAskResponse(
        Long conversationId,
        String message,
        List<SiteLink> links,
        List<String> quickReplies
) {}
