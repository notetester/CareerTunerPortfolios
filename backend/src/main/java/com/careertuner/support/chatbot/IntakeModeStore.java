package com.careertuner.support.chatbot;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 오케스트레이터(인테이크) <b>sticky 모드</b> 활성 플래그 저장소.
 *
 * <p>{@link RouteConfirmStore}(확인 1턴 플래그)와 별개다. 이쪽은 한 번 ③ 입구로 진입한 뒤
 * 그 대화가 인테이크 모드에 <b>머무르게</b> 한다 — 다음 턴이 다시 라우팅을 받아 ①(FAQ)로 새지 않도록.</p>
 *
 * <p>전이: INTAKE_DIRECT/[시작 확인] → {@link #enter}. ready(실행 위임)·이탈("그만"/⏏) → {@link #exit}.
 * RouteConfirmStore 와 같은 인메모리 패턴(세션 영속화는 별도 단계).</p>
 */
@Component
public class IntakeModeStore {

    private final Set<Long> active = ConcurrentHashMap.newKeySet();

    /** 인테이크 모드 진입(이후 턴은 라우팅 스킵하고 ③ 직행). */
    public void enter(Long conversationId) {
        if (conversationId != null) {
            active.add(conversationId);
        }
    }

    /** 인테이크 모드 해제(일반 모드 복귀 또는 RUN 위임). */
    public void exit(Long conversationId) {
        if (conversationId != null) {
            active.remove(conversationId);
        }
    }

    /** 이 대화가 인테이크 모드를 유지 중인지. */
    public boolean isActive(Long conversationId) {
        return conversationId != null && active.contains(conversationId);
    }
}
