package com.careertuner.support.chatbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 오분류 안전판의 "확인 대기" 1턴 플래그 저장소.
 *
 * <p>경계구역 COMMAND 로 분류된 발화는 인테이크로 바로 끌고 가지 않고 "면접 준비를 도와드릴까요?"
 * 확인을 1회 띄운다. 그 사이 conversationId 에 대기 표시를 둔다.</p>
 *
 * <p><b>범위 경계:</b> 이 플래그는 <b>확인 1턴</b>만을 위한 것이다. 인테이크 진입 후 거기에 머무르는
 * sticky 모드 표현에 절대 쓰지 않는다(다음 PR). 다음 턴에 무조건 소비(clear)된다.</p>
 */
@Component
public class RouteConfirmStore {

    private final Map<Long, String> pending = new ConcurrentHashMap<>();

    /** 확인 응답을 띄울 때 대기 표시. */
    public void markPending(Long conversationId, String originalQuestion) {
        if (conversationId != null) {
            pending.put(conversationId, originalQuestion == null ? "" : originalQuestion);
        }
    }

    /** 대기 중이면 최초 발화를 돌려주고 즉시 소비한다. 대기 아니면 null. */
    public String consumePendingQuestion(Long conversationId) {
        return conversationId == null ? null : pending.remove(conversationId);
    }
}
