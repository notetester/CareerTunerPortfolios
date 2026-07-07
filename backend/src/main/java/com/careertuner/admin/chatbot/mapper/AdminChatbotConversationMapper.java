package com.careertuner.admin.chatbot.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.chatbot.dto.AdminChatbotConversationRow;

/** 관리자 챗봇 대화 세션 조회/삭제(chatbot_conversation_memory). */
@Mapper
public interface AdminChatbotConversationMapper {

    List<AdminChatbotConversationRow> findRecent(@Param("userId") Long userId,
                                                 @Param("limit") int limit);

    int deleteConversation(@Param("conversationId") Long conversationId);
}
