package com.careertuner.support.chatbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * ③ 인테이크/④ 온보딩이 "답변 대기" 중일 때 감지된 <b>이탈성 질문</b>의 확인 1턴 보류 저장소.
 *
 * <p>수집 단계에서 질문처럼 보이는 발화(물음표 종결 또는 FAQ top-1 압도)는 바로 답으로 저장하지 않고
 * "질문이신 것 같아요 — 답해드릴까요?" 확인을 1회 띄운다. 그 사이 원 발화를 여기 보관했다가,
 * "네"면 그 발화를 ①(FAQ/에이전트)로 우회 답변하고, "아니요"면 원 발화를 답변으로 저장한다.
 * 어느 쪽이든 ③/④ 진행 상태(step·슬롯·sticky)는 건드리지 않는다.</p>
 *
 * <p>{@link RouteConfirmStore} 와 같은 인메모리 1턴 패턴 — 다음 턴에 무조건 소비(consume)된다.
 * 재시작 시 휘발되지만 확인 대기가 사라질 뿐 데이터 오염은 없다(안전한 방향).</p>
 */
@Component
public class SideQuestionStore {

    private final Map<Long, String> pending = new ConcurrentHashMap<>();

    /** 확인 1턴을 띄울 때 원 발화를 보류한다. */
    public void defer(Long conversationId, String question) {
        if (conversationId != null && question != null) {
            pending.put(conversationId, question);
        }
    }

    /** 보류 발화를 돌려주고 즉시 소비한다(1턴 한정). 대기 없으면 null. */
    public String consume(Long conversationId) {
        return conversationId == null ? null : pending.remove(conversationId);
    }
}
