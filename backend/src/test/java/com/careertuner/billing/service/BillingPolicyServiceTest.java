package com.careertuner.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.billing.domain.AiFeatureBenefitPolicy;
import com.careertuner.billing.domain.UserSubscription;
import com.careertuner.billing.mapper.BillingMapper;
import com.careertuner.billing.mapper.BillingPolicyChangeMapper;

import tools.jackson.databind.ObjectMapper;

class BillingPolicyServiceTest {

    @Test
    void hydratesCreditRangeMissingFromLegacySubscriptionSnapshot() {
        BillingMapper billingMapper = mock(BillingMapper.class);
        BillingPolicyChangeMapper changeMapper = mock(BillingPolicyChangeMapper.class);
        BillingPolicyService service = new BillingPolicyService(billingMapper, changeMapper, new ObjectMapper());

        String legacySnapshot = """
                {"featureBenefitPolicies":[{
                  "featureType":"JOB_ANALYSIS",
                  "benefitCode":"APPLICATION_ANALYSIS",
                  "chargeUnit":"PER_CASE",
                  "includedInTicket":true,
                  "defaultCreditCost":1,
                  "active":true
                }]}
                """;
        when(billingMapper.findActiveSubscription(any(), any()))
                .thenReturn(UserSubscription.builder().policySnapshotJson(legacySnapshot).build());

        AiFeatureBenefitPolicy current = new AiFeatureBenefitPolicy();
        current.setFeatureType("JOB_ANALYSIS");
        current.setActive(true);
        current.setMinCreditCost(1);
        current.setMaxCreditCost(5);
        current.setCreditUnitTokens(1_000);
        when(billingMapper.findActiveFeatureBenefitPolicy("JOB_ANALYSIS")).thenReturn(current);

        AiFeatureBenefitPolicy result = service.activeFeatureBenefitPolicy(1L, "JOB_ANALYSIS");

        assertThat(result.getMinCreditCost()).isEqualTo(1);
        assertThat(result.getMaxCreditCost()).isEqualTo(5);
        assertThat(result.getCreditUnitTokens()).isEqualTo(1_000);
    }
}
