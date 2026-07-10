package com.careertuner.ai.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
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
        return normalizeSystemFirst(ChatMessageDeserializer.messagesFromJson(json));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Long conversationId = toLong(memoryId);
        mapper.upsert(conversationId, ChatMessageSerializer.messagesToJson(normalizeSystemFirst(messages)));
        stampTitleFromFirstUserMessage(conversationId, messages);
    }

    /**
     * 일반 대화도 목록에서 구분되도록, 첫 사용자 발화 앞부분을 제목으로 1회 스탬프한다.
     * user 메시지가 정확히 1개인 턴(=첫 턴)에만 시도하고, WHERE title IS NULL 가드라
     * 인테이크 세션의 bindCase 제목("{회사} {직무}")과도 충돌하지 않는다(그쪽은 무조건 UPDATE로 덮음).
     */
    private void stampTitleFromFirstUserMessage(Long conversationId, List<ChatMessage> messages) {
        dev.langchain4j.data.message.UserMessage first = null;
        int userCount = 0;
        for (ChatMessage m : messages) {
            if (m instanceof dev.langchain4j.data.message.UserMessage u) {
                userCount++;
                if (first == null) {
                    first = u;
                }
            }
        }
        if (userCount != 1 || first == null) {
            return;
        }
        String text = first.singleText();
        if (text == null || text.isBlank()) {
            return;
        }
        String title = text.strip().replaceAll("\\s+", " ");
        if (title.length() > 40) {
            title = title.substring(0, 40) + "…";
        }
        try {
            mapper.updateTitleIfNull(conversationId, title);
        } catch (RuntimeException ex) {
            // 제목은 부가 정보 — 실패해도 대화 저장 자체를 깨지 않는다.
        }
    }

    /**
     * SYSTEM 메시지를 항상 리스트 맨 앞으로 정렬한다(여럿이면 가장 최근 1개만 유지).
     *
     * <p>케이스 전환 fork 의 구간 복사(subList)가 SYSTEM 을 누락시키면, 다음 agent.chat() 의 재추가가
     * 리스트 끝에 붙어 "SYSTEM 중간 위치" 대화가 되고 qwen3 가 규칙을 놓친다(에코 응답·무근거 tool call — C2).
     * 저장·로드 공통 관문인 여기서 위치를 강제해 fork·에이전트 전환(①↔③) 등 모든 경로를 한 번에 방어하고,
     * 이미 오염된 기존 행도 다음 로드에서 치유된다. 윈도우 evict 의 index0 SYSTEM 보호
     * (langchain4j ensureCapacity)와 정합이라 개수·순서 외 부작용 없음.
     */
    private static List<ChatMessage> normalizeSystemFirst(List<ChatMessage> messages) {
        SystemMessage system = null;
        int systemCount = 0;
        for (ChatMessage m : messages) {
            if (m instanceof SystemMessage s) {
                system = s;          // 여럿이면 마지막(=가장 최근 에이전트의 프롬프트)이 이긴다
                systemCount++;
            }
        }
        if (system == null || (systemCount == 1 && messages.get(0) instanceof SystemMessage)) {
            return messages;         // SYSTEM 없음 또는 이미 정상 위치 — 그대로
        }
        List<ChatMessage> normalized = new ArrayList<>(messages.size());
        normalized.add(system);
        for (ChatMessage m : messages) {
            if (!(m instanceof SystemMessage)) {
                normalized.add(m);
            }
        }
        return normalized;
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

    /** 유저의 최근 대화 목록(인테이크+일반, 최근순 최대 20). 사이드바 "대화 목록"용. */
    public List<Map<String, Object>> listRecentConversations(Long userId) {
        return mapper.findRecentConversationsByUser(userId);
    }

    /** 대화 소유자 user_id(없으면 null). 메시지 조회 권한 확인용. */
    public Long findOwnerUserId(Long conversationId) {
        return mapper.findOwnerUserId(conversationId);
    }

    /** 이 대화에 바인딩된 지원 건 id(없으면 null). fork 가드 영속 권위 — boundCaseId 인메모리 증발 시 DB 보강. */
    public Long findApplicationCaseId(Long conversationId) {
        return conversationId == null ? null : mapper.findApplicationCaseId(conversationId);
    }

    /** 이 대화가 온보딩을 거부했는지(영속 권위). 깡통계정이어도 거부 후엔 온보딩 재진입을 막는다. */
    public boolean isOnboardingDeclined(Long conversationId) {
        return conversationId != null && mapper.findOnboardingDeclinedAt(conversationId) != null;
    }

    /** 이 대화의 온보딩 거부를 DB 에 영속(재시작 후에도 유지). */
    public void markOnboardingDeclined(Long conversationId) {
        if (conversationId != null) {
            mapper.markOnboardingDeclined(conversationId);
        }
    }

    /** 온보딩 거부 해제(재시작 확인 "네" 응답 시) — 같은 대화에서 마음을 바꾼 유저의 재진입을 허용한다. */
    public void clearOnboardingDeclined(Long conversationId) {
        if (conversationId != null) {
            mapper.clearOnboardingDeclined(conversationId);
        }
    }

    private Long toLong(Object memoryId) {
        if (memoryId instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(memoryId));
    }
}
