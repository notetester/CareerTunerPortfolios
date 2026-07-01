package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.dto.AiChargeCommand;
import com.careertuner.billing.dto.AiChargeResult;
import com.careertuner.billing.dto.BenefitConsumeResult;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.dto.CreditDeductionResult;
import com.careertuner.credit.mapper.CreditMapper;
import com.careertuner.credit.service.CreditService;

class AiChargeServiceImplTest {

    private final BillingPolicyService billingPolicyService = org.mockito.Mockito.mock(BillingPolicyService.class);
    private final RefundPolicyService refundPolicyService = org.mockito.Mockito.mock(RefundPolicyService.class);
    private final AiBenefitUsageService benefitUsageService = org.mockito.Mockito.mock(AiBenefitUsageService.class);
    private final CreditService creditService = org.mockito.Mockito.mock(CreditService.class);
    private final CreditMapper creditMapper = org.mockito.Mockito.mock(CreditMapper.class);
    private final AiChargeServiceImpl service = new AiChargeServiceImpl(
            billingPolicyService,
            refundPolicyService,
            benefitUsageService,
            creditService,
            creditMapper);

    @Test
    void ticketConsumptionDoesNotDeductCredit() {
        AiChargeCommand command = command(10L, 3);
        when(billingPolicyService.activeFeatureBenefitPolicy(1L, "JOB_ANALYSIS"))
                .thenReturn(featurePolicy(true, 3));
        when(billingPolicyService.activeBenefitPolicy(1L, "APPLICATION_ANALYSIS"))
                .thenReturn(benefitPolicy("BLOCK", 3));
        when(benefitUsageService.consumeByFeature(1L, "JOB_ANALYSIS", "APPLICATION_CASE", 100L, 10L, "analysis"))
                .thenReturn(BenefitConsumeResult.consumed("APPLICATION_ANALYSIS", 2));

        AiChargeResult result = service.charge(command);

        assertThat(result.chargeType()).isEqualTo(AiChargeResult.ChargeType.TICKET);
        assertThat(result.benefitCode()).isEqualTo("APPLICATION_ANALYSIS");
        assertThat(result.remainingTicket()).isEqualTo(2);
        verify(creditService, never()).deductByAiUsageLog(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void alreadyConsumedTicketDoesNotDeductCreditAgain() {
        AiChargeCommand command = command(10L, 3);
        when(billingPolicyService.activeFeatureBenefitPolicy(1L, "JOB_ANALYSIS"))
                .thenReturn(featurePolicy(true, 3));
        when(billingPolicyService.activeBenefitPolicy(1L, "APPLICATION_ANALYSIS"))
                .thenReturn(benefitPolicy("CREDIT", 3));
        when(benefitUsageService.consumeByFeature(1L, "JOB_ANALYSIS", "APPLICATION_CASE", 100L, 10L, "analysis"))
                .thenReturn(BenefitConsumeResult.skipped("APPLICATION_ANALYSIS", 1, "ALREADY_CONSUMED"));
        when(creditMapper.findUserCredit(1L)).thenReturn(8);

        AiChargeResult result = service.charge(command);

        assertThat(result.chargeType()).isEqualTo(AiChargeResult.ChargeType.SKIPPED);
        assertThat(result.reason()).isEqualTo("ALREADY_CHARGED");
        assertThat(result.remainingCredit()).isEqualTo(8);
        verify(creditService, never()).deductByAiUsageLog(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void insufficientTicketFallsBackToCreditWhenPolicyAllows() {
        AiChargeCommand command = command(10L, null);
        when(billingPolicyService.activeFeatureBenefitPolicy(1L, "JOB_ANALYSIS"))
                .thenReturn(featurePolicy(true, 5));
        when(billingPolicyService.activeBenefitPolicy(1L, "APPLICATION_ANALYSIS"))
                .thenReturn(benefitPolicy("CREDIT", 2));
        when(benefitUsageService.consumeByFeature(1L, "JOB_ANALYSIS", "APPLICATION_CASE", 100L, 10L, "analysis"))
                .thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_CREDIT));
        when(creditService.deductByAiUsageLog(10L, 2))
                .thenReturn(CreditDeductionResult.deducted(10L, 1L, 2, 6));

        AiChargeResult result = service.charge(command);

        assertThat(result.chargeType()).isEqualTo(AiChargeResult.ChargeType.CREDIT);
        assertThat(result.chargedCredit()).isEqualTo(2);
        assertThat(result.remainingCredit()).isEqualTo(6);
    }

    @Test
    void insufficientTicketDoesNotFallbackWhenPolicyBlocks() {
        AiChargeCommand command = command(10L, null);
        when(billingPolicyService.activeFeatureBenefitPolicy(1L, "JOB_ANALYSIS"))
                .thenReturn(featurePolicy(true, 5));
        when(billingPolicyService.activeBenefitPolicy(1L, "APPLICATION_ANALYSIS"))
                .thenReturn(benefitPolicy("BLOCK", 2));
        when(benefitUsageService.consumeByFeature(1L, "JOB_ANALYSIS", "APPLICATION_CASE", 100L, 10L, "analysis"))
                .thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_CREDIT));

        assertThatThrownBy(() -> service.charge(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_CREDIT);

        verify(creditService, never()).deductByAiUsageLog(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void featureWithoutTicketPolicyChargesCreditOnly() {
        AiChargeCommand command = command(10L, 4);
        when(billingPolicyService.activeFeatureBenefitPolicy(1L, "JOB_ANALYSIS"))
                .thenReturn(featurePolicy(false, 5));
        when(creditService.deductByAiUsageLog(10L, 4))
                .thenReturn(CreditDeductionResult.deducted(10L, 1L, 4, 4));

        AiChargeResult result = service.charge(command);

        assertThat(result.chargeType()).isEqualTo(AiChargeResult.ChargeType.CREDIT);
        assertThat(result.chargedCredit()).isEqualTo(4);
        verify(benefitUsageService, never()).consumeByFeature(anyLong(), anyString(), anyString(), anyLong(), any(), any());
    }

    private static AiChargeCommand command(Long aiUsageLogId, Integer creditCost) {
        return new AiChargeCommand(
                1L,
                "JOB_ANALYSIS",
                "APPLICATION_CASE",
                100L,
                aiUsageLogId,
                creditCost,
                "analysis",
                "test-action");
    }

    private static AiFeatureBenefitPolicy featurePolicy(boolean includedInTicket, int defaultCreditCost) {
        AiFeatureBenefitPolicy policy = new AiFeatureBenefitPolicy();
        policy.setFeatureType("JOB_ANALYSIS");
        policy.setBenefitCode("APPLICATION_ANALYSIS");
        policy.setIncludedInTicket(includedInTicket);
        policy.setDefaultCreditCost(defaultCreditCost);
        policy.setActive(true);
        return policy;
    }

    private static SubscriptionBenefitPolicy benefitPolicy(String overagePolicy, int creditCost) {
        SubscriptionBenefitPolicy policy = new SubscriptionBenefitPolicy();
        policy.setPlanCode("BASIC");
        policy.setBenefitCode("APPLICATION_ANALYSIS");
        policy.setOveragePolicy(overagePolicy);
        policy.setCreditCost(creditCost);
        policy.setActive(true);
        return policy;
    }

    @SuppressWarnings("unused")
    private static UserSubscription subscription(String planCode) {
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(1L);
        subscription.setPlanCode(planCode);
        subscription.setStatus("ACTIVE");
        return subscription;
    }
}
