package com.careertuner.support.chatbot;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * ④ 온보딩 "재시작" 확인 1턴의 대기 플래그 저장소.
 *
 * <p>수집 대기 중(진행 중 또는 declined 이후) 재시작 의도 발화("면접 해줘"/"다시"/"처음부터" 등)가
 * 오면 즉시 리셋하지 않고 "처음부터 다시 시작할까요?" 확인을 1회 띄운다. 그 사이 대기 표시만 두고
 * (원 발화는 보관하지 않는다 — 재시작 여부만 판정하면 되고, "아니요" 시 원래 단계를 다시 보여줄 뿐
 * 그 발화를 데이터로 쓰지 않는다), "네"면 호출부가 온보딩 상태를 리셋(또는 declined 해제)한다.</p>
 *
 * <p>{@link RouteConfirmStore}·{@link SideQuestionStore} 와 같은 인메모리 1턴 패턴 — 다음 턴에
 * 무조건 소비(consume)된다. 진행 중(step 기반) 트리거와 declined 트리거는 서로 배타적 상태에서만
 * 발생하므로(동시에 참일 수 없음) 하나의 저장소를 공유해도 안전하다.</p>
 */
@Component
public class OnboardingRestartStore {

    private final Set<Long> pending = ConcurrentHashMap.newKeySet();

    /** 재시작 확인 1턴을 띄울 때 대기 표시. */
    public void defer(Long conversationId) {
        if (conversationId != null) {
            pending.add(conversationId);
        }
    }

    /** 대기 중이었는지 돌려주고 즉시 소비한다(1턴 한정). */
    public boolean consume(Long conversationId) {
        return conversationId != null && pending.remove(conversationId);
    }
}
