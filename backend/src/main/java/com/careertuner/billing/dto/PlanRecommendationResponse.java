package com.careertuner.billing.dto;

import java.util.List;

/**
 * 사용량 기반 요금제/크레딧 추천(AI 기능 #28, USAGE_PLAN_RECOMMENDATION) 결과.
 *
 * <p><b>결정론(규칙 기반)</b>이며 LLM 을 호출하지 않는다 — 담당별 자체LLM 운영안의 "1차는 규칙 기반, 결제
 * 강요·과장 추천 금지" 원칙을 따른다. 추천은 이번 달 실사용량(ai_usage_log 집계)·보유 크레딧·현재 요금제만으로
 * 계산하고, 판단을 넘어서는 강권은 하지 않는다(과사용이 아니면 기본값은 KEEP).
 *
 * @param recommendation     UPGRADE_PLAN | BUY_CREDITS | KEEP
 * @param currentPlanCode    현재 요금제 코드
 * @param currentPlanName    현재 요금제 이름
 * @param creditBalance      보유 크레딧
 * @param monthlyUsageCount  이번 달 AI 기능 사용 횟수(전체 합)
 * @param monthlyCreditUsed  이번 달 소모 크레딧 합
 * @param topFeatureType     가장 많이 쓴 기능 타입(라벨은 프런트 getAiFeatureLabel). 사용 없으면 null
 * @param headline           사용자에게 보여줄 한 줄 결론
 * @param reasons            결정 근거(결정론 신호)
 * @param recommendedPlan    UPGRADE_PLAN 일 때만 채워짐(아니면 null)
 * @param recommendedCreditPack BUY_CREDITS 일 때만 채워짐(아니면 null)
 */
public record PlanRecommendationResponse(
        String recommendation,
        String currentPlanCode,
        String currentPlanName,
        int creditBalance,
        int monthlyUsageCount,
        int monthlyCreditUsed,
        String topFeatureType,
        String headline,
        List<String> reasons,
        RecommendedPlan recommendedPlan,
        RecommendedCreditPack recommendedCreditPack) {

    public record RecommendedPlan(String code, String name, int monthlyPrice) {
    }

    public record RecommendedCreditPack(String code, String name, int price, int creditAmount) {
    }
}
