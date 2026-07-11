package com.careertuner.ai.common.model;

import java.util.List;

/**
 * '<b>선택 tier 부터 시작 + 하위 폴백</b>' 시도 순서 계산기(순수 함수). 각 도메인 디스패처가 이 순서로 tier 를
 * 순회하면 "선택 tier 위쪽은 skip, 아래쪽은 폴백 유지, 최종은 안전망" 규칙을 도메인마다 재구현하지 않아도 된다.
 *
 * <p>예) 기본 순서 {@code [CAREERTUNER, CLAUDE, OPENAI]} 에서
 * {@code AUTO}/{@code CAREERTUNER} → 전체, {@code CLAUDE} → {@code [CLAUDE, OPENAI]},
 * {@code OPENAI} → {@code [OPENAI]}.
 */
public final class AiProviderChain {

    private AiProviderChain() {
    }

    /**
     * @param choice       사용자 선택({@link RequestedAiModel#AUTO} 또는 null 이면 {@code defaultOrder} 전체)
     * @param defaultOrder 이 도메인의 기본 tier 순서(예: {@code [CAREERTUNER, CLAUDE, OPENAI]})
     * @return 시도 순서 — 선택 tier 가 {@code defaultOrder} 에 있으면 그 지점부터 끝까지, 없거나 AUTO 면 전체(fail-open).
     */
    public static List<AiProviderTier> startingFrom(RequestedAiModel choice, List<AiProviderTier> defaultOrder) {
        if (choice == null || choice == RequestedAiModel.AUTO) {
            return List.copyOf(defaultOrder);
        }
        AiProviderTier tier = choice.tier();
        int idx = defaultOrder.indexOf(tier);
        if (idx < 0) {
            // 이 도메인이 제공하지 않는 tier 를 골랐다 → 기본 순서로 폴백(선택은 무시하되 화면은 정상).
            return List.copyOf(defaultOrder);
        }
        return List.copyOf(defaultOrder.subList(idx, defaultOrder.size()));
    }
}
