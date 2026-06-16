package com.careertuner.fitanalysis.ai;

import java.util.List;

/**
 * "지원해도 되는가?" 최종 판단 카드.
 *
 * <p>decision: APPLY(지원 가능)/COMPLEMENT(보완 후 지원)/HOLD(지원 보류).
 * 판단 이유와 지원 전 실행할 행동을 함께 제공한다.
 */
public record FitApplyDecision(
        String decision,
        List<String> reasons,
        List<String> actions
) {
}
