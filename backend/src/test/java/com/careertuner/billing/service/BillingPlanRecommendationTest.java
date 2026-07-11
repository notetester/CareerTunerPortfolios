package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.billing.domain.SubscriptionPlan;
import com.careertuner.billing.dto.PlanRecommendationResponse;
import com.careertuner.billing.dto.UsageRow;
import com.careertuner.billing.mapper.BillingMapper;
import com.careertuner.credit.domain.CreditProduct;
import com.careertuner.notification.service.NotificationService;

/**
 * 사용량 기반 요금제 추천(결정론)의 세 분기 검증. 규칙엔진처럼 판단은 규칙이 소유하고 LLM 은 개입하지 않는다.
 */
class BillingPlanRecommendationTest {

    private final BillingMapper billingMapper = mock(BillingMapper.class);
    private final BillingPolicyService billingPolicyService = mock(BillingPolicyService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final BillingServiceImpl service =
            new BillingServiceImpl(billingMapper, billingPolicyService, notificationService);

    private static SubscriptionPlan plan(String code, String name, int price) {
        return SubscriptionPlan.builder().code(code).name(name).monthlyPrice(price)
                .yearlyPrice(price * 10).description(name).active(true).sortOrder(price).build();
    }

    private static UsageRow usage(String feature, int used, int credit) {
        return UsageRow.builder().featureType(feature).used(used).creditUsed(credit).build();
    }

    private static CreditProduct creditProduct(String code, String name, int price, int amount) {
        CreditProduct p = new CreditProduct();
        p.setCode(code);
        p.setName(name);
        p.setPrice(price);
        p.setCreditAmount(amount);
        p.setEnabled(true);
        return p;
    }

    @BeforeEach
    void setUp() {
        // 요금제 사다리 FREE(0) < BASIC(9900) < PRO(19900). listPlans() 는 benefit 이 비어도 동작.
        lenient().when(billingPolicyService.activeBenefitPolicies()).thenReturn(List.of());
        lenient().when(billingPolicyService.activePlans()).thenReturn(List.of(
                plan("FREE", "무료", 0), plan("BASIC", "베이직", 9900), plan("PRO", "프로", 19900)));
        // getMyBilling: 구독 없음 → FREE 취급.
        lenient().when(billingMapper.findActiveSubscription(any(), any())).thenReturn(null);
        lenient().when(billingMapper.findPlanByCode("FREE")).thenReturn(plan("FREE", "무료", 0));
    }

    @Test
    void heavyUsageRecommendsUpgradeToNextPlan() {
        when(billingMapper.monthlyUsage(eq(1L), any())).thenReturn(List.of(
                usage("FIT_ANALYSIS", 10, 0), usage("JOB_ANALYSIS", 6, 0))); // 합 16 ≥ 15
        when(billingMapper.findUserCredit(1L)).thenReturn(20);

        PlanRecommendationResponse r = service.recommendPlan(1L);

        assertThat(r.recommendation()).isEqualTo("UPGRADE_PLAN");
        assertThat(r.monthlyUsageCount()).isEqualTo(16);
        assertThat(r.topFeatureType()).isEqualTo("FIT_ANALYSIS");
        assertThat(r.recommendedPlan()).isNotNull();
        assertThat(r.recommendedPlan().code()).isEqualTo("BASIC"); // FREE 바로 위 유료
        assertThat(r.recommendedCreditPack()).isNull();
        assertThat(r.headline()).contains("베이직");
    }

    @Test
    void lowCreditWithSteadyUsageRecommendsCreditPack() {
        when(billingMapper.monthlyUsage(eq(1L), any())).thenReturn(List.of(
                usage("INTERVIEW_QUESTION", 5, 12))); // 5회(<15, 업그레이드 조건 미달) · ≥3
        when(billingMapper.findUserCredit(1L)).thenReturn(2); // ≤5
        when(billingPolicyService.enabledCreditProducts()).thenReturn(List.of(
                creditProduct("CREDIT_30", "크레딧 30", 3000, 30),
                creditProduct("CREDIT_100", "크레딧 100", 9000, 100)));

        PlanRecommendationResponse r = service.recommendPlan(1L);

        assertThat(r.recommendation()).isEqualTo("BUY_CREDITS");
        assertThat(r.recommendedCreditPack()).isNotNull();
        assertThat(r.recommendedCreditPack().code()).isEqualTo("CREDIT_30"); // 30 이상 최소 팩
        assertThat(r.recommendedPlan()).isNull();
    }

    @Test
    void moderateUsageKeepsCurrentPlan() {
        when(billingMapper.monthlyUsage(eq(1L), any())).thenReturn(List.of(
                usage("FIT_ANALYSIS", 2, 0))); // 2회 — 업그레이드도 충전도 아님
        when(billingMapper.findUserCredit(1L)).thenReturn(25);

        PlanRecommendationResponse r = service.recommendPlan(1L);

        assertThat(r.recommendation()).isEqualTo("KEEP");
        assertThat(r.recommendedPlan()).isNull();
        assertThat(r.recommendedCreditPack()).isNull();
        assertThat(r.headline()).contains("적절");
    }

    @Test
    void noUsageKeepsPlanWithHonestMessage() {
        when(billingMapper.monthlyUsage(eq(1L), any())).thenReturn(List.of());
        when(billingMapper.findUserCredit(1L)).thenReturn(30);

        PlanRecommendationResponse r = service.recommendPlan(1L);

        assertThat(r.recommendation()).isEqualTo("KEEP");
        assertThat(r.monthlyUsageCount()).isZero();
        assertThat(r.topFeatureType()).isNull();
        assertThat(r.headline()).contains("사용 기록이 없");
    }
}
