package com.careertuner.ai.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * LangChain4j 대화 메모리를 MySQL(chatbot_conversation_memory)에 영속화.
 * <p>memoryId = conversationId(Long). 메시지 윈도우 전체를 JSON 으로 직렬화해 행 단위로 보관.
 * 새로고침/재시작 후에도 conversationId 로 복원된다.
 */
@Component
public class MyBatisChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryMapper mapper;

    public MyBatisChatMemoryStore(ChatMemoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = mapper.findMessages(toLong(memoryId));
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        mapper.upsert(toLong(memoryId), ChatMessageSerializer.messagesToJson(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        mapper.delete(toLong(memoryId));
    }

    /**
     * 새 대화 행을 만들고 발급된 conversationId 를 반환.
     * @param userId 로그인 유저 id(소유자). 비로그인이면 null → 익명 대화(복원 대상 아님).
     */
    public Long createConversation(Long userId) {
        Map<String, Object> holder = new HashMap<>();
        holder.put("userId", userId);
        mapper.createConversation(holder);
        return ((Number) holder.get("id")).longValue();
    }

    /** 해당 유저의 가장 최근 대화 id (없으면 null). 복원 시작점. */
    public Long findRecentConversation(Long userId) {
        return mapper.findRecentConversationByUser(userId);
    }

    /** fork 로 만든 대화에 지원 건 id·제목을 바인딩한다(지원건 세션 표식 — 세션 목록/매핑). */
    public void bindCase(Long conversationId, Long applicationCaseId, String title) {
        mapper.bindCase(conversationId, applicationCaseId, title);
    }

    private Long toLong(Object memoryId) {
        if (memoryId instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(memoryId));
    }
}
