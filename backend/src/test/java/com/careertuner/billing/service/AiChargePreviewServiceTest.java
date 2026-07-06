package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;
import com.careertuner.billing.domain.RefundPolicy;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.dto.AiChargePreviewRequest;
import com.careertuner.billing.dto.MyBenefitsResponse;
import com.careertuner.billing.dto.UserBenefitBalanceResponse;
import com.careertuner.credit.mapper.CreditMapper;

class AiChargePreviewServiceTest {

    private final BillingPolicyService policyService = org.mockito.Mockito.mock(BillingPolicyService.class);
    private final BillingService billingService = org.mockito.Mockito.mock(BillingService.class);
    private final CreditMapper creditMapper = org.mockito.Mockito.mock(CreditMapper.class);
    private final RefundPolicyService refundPolicyService = org.mockito.Mockito.mock(RefundPolicyService.class);
    private final AiChargePreviewService service = new AiChargePreviewService(
            policyService, billingService, creditMapper, refundPolicyService);

    @Test
    void previewsTicketWhenBenefitRemains() {
        givenBase(10);
        when(policyService.activeFeatureBenefitPolicy(1L, "CORRECTION_SELF_INTRO"))
                .thenReturn(featurePolicy(true, 2));
        when(billingService.myBenefits(1L)).thenReturn(benefits(3));

        var result = service.preview(1L, request());

        assertThat(result.chargeType()).isEqualTo("TICKET");
        assertThat(result.chargeAmount()).isEqualTo(1);
        assertThat(result.triggerType()).isEqualTo("BENEFIT_USE");
        assertThat(result.remainingTicket()).isEqualTo(3);
    }

    @Test
    void previewsCreditFallbackWhenTicketIsEmpty() {
        givenBase(10);
        when(policyService.activeFeatureBenefitPolicy(1L, "CORRECTION_SELF_INTRO"))
                .thenReturn(featurePolicy(true, 2));
        when(billingService.myBenefits(1L)).thenReturn(benefits(0));
        when(policyService.activeBenefitPolicy(1L, "CORRECTION_SELF_INTRO"))
                .thenReturn(benefitPolicy("CREDIT", 2));

        var result = service.preview(1L, request());

        assertThat(result.chargeType()).isEqualTo("CREDIT");
        assertThat(result.chargeAmount()).isEqualTo(2);
        assertThat(result.sufficient()).isTrue();
        assertThat(result.triggerType()).isEqualTo("CREDIT_USE");
    }

    @Test
    void marksCreditPreviewInsufficientWithoutChangingBalance() {
        givenBase(1);
        when(policyService.activeFeatureBenefitPolicy(1L, "CORRECTION_SELF_INTRO"))
                .thenReturn(featurePolicy(false, 2));

        var result = service.preview(1L, request());

        assertThat(result.chargeType()).isEqualTo("CREDIT");
        assertThat(result.sufficient()).isFalse();
        assertThat(result.currentCredit()).isEqualTo(1);
    }

    @Test
    void blocksWhenTicketIsEmptyAndFallbackIsDisabled() {
        givenBase(10);
        when(policyService.activeFeatureBenefitPolicy(1L, "CORRECTION_SELF_INTRO"))
                .thenReturn(featurePolicy(true, 2));
        when(billingService.myBenefits(1L)).thenReturn(benefits(0));
        when(policyService.activeBenefitPolicy(1L, "CORRECTION_SELF_INTRO"))
                .thenReturn(benefitPolicy("BLOCK", 0));

        var result = service.preview(1L, request());

        assertThat(result.chargeType()).isEqualTo("BLOCKED");
        assertThat(result.sufficient()).isFalse();
    }

    @Test
    void previewsMinimumFirstAndRequiresEnoughCreditForMaximum() {
        givenBase(4);
        AiFeatureBenefitPolicy policy = featurePolicy(false, 2);
        policy.setMinCreditCost(2);
        policy.setMaxCreditCost(5);
        policy.setCreditUnitTokens(1_000);
        when(policyService.activeFeatureBenefitPolicy(1L, "CORRECTION_SELF_INTRO"))
                .thenReturn(policy);

        var result = service.preview(1L, request());

        assertThat(result.chargeAmount()).isEqualTo(2);
        assertThat(result.minimumCreditCost()).isEqualTo(2);
        assertThat(result.maximumCreditCost()).isEqualTo(5);
        assertThat(result.creditUnitTokens()).isEqualTo(1_000);
        assertThat(result.usageBased()).isTrue();
        assertThat(result.sufficient()).isFalse();
    }

    private void givenBase(int credit) {
        when(creditMapper.findUserCredit(1L)).thenReturn(credit);
        when(refundPolicyService.currentPolicy()).thenReturn(refundPolicy());
    }

    private static AiChargePreviewRequest request() {
        return new AiChargePreviewRequest("CORRECTION_SELF_INTRO", null, "AI_USAGE:test-1");
    }

    private static AiFeatureBenefitPolicy featurePolicy(boolean included, int cost) {
        AiFeatureBenefitPolicy policy = new AiFeatureBenefitPolicy();
        policy.setFeatureType("CORRECTION_SELF_INTRO");
        policy.setBenefitCode("CORRECTION_SELF_INTRO");
        policy.setIncludedInTicket(included);
        policy.setDefaultCreditCost(cost);
        policy.setMinCreditCost(cost);
        policy.setMaxCreditCost(cost);
        policy.setCreditUnitTokens(1_000);
        return policy;
    }

    private static SubscriptionBenefitPolicy benefitPolicy(String overagePolicy, int cost) {
        SubscriptionBenefitPolicy policy = new SubscriptionBenefitPolicy();
        policy.setBenefitCode("CORRECTION_SELF_INTRO");
        policy.setOveragePolicy(overagePolicy);
        policy.setCreditCost(cost);
        return policy;
    }

    private static MyBenefitsResponse benefits(int remaining) {
        LocalDateTime now = LocalDateTime.now();
        return new MyBenefitsResponse(
                "BASIC",
                now,
                now.plusMonths(1),
                List.of(new UserBenefitBalanceResponse(
                        "CORRECTION_SELF_INTRO",
                        "자기소개서 첨삭",
                        5,
                        5 - remaining,
                        remaining,
                        "BASIC",
                        now,
                        now.plusMonths(1))));
    }

    private static RefundPolicy refundPolicy() {
        RefundPolicy policy = new RefundPolicy();
        policy.setId(1L);
        policy.setVersion(2);
        policy.setTitle("환불 정책");
        policy.setSummary("결제 및 사용 전 고지");
        policy.setRulesJson("{}");
        return policy;
    }
}
