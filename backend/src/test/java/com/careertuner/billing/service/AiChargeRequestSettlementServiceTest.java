package com.careertuner.billing.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AiChargeRequestSettlementServiceTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void settlesOnlyAcknowledgedFeatureAndOnlyOncePerRequest() {
        AiChargeService chargeService = mock(AiChargeService.class);
        AiChargeRequestSettlementService service = new AiChargeRequestSettlementService(chargeService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AiChargeRequestSettlementService.ACKNOWLEDGEMENT_HEADER, "ack-1");
        request.addHeader(AiChargeRequestSettlementService.FEATURE_HEADER, "INTERVIEW_ANSWER_EVAL");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.settleFirstAcknowledgedUsage(1L, "INTERVIEW_MODEL_ANSWER", 10L, 1200);
        service.settleFirstAcknowledgedUsage(1L, "INTERVIEW_ANSWER_EVAL", 11L, 2400);
        service.settleFirstAcknowledgedUsage(1L, "INTERVIEW_ANSWER_EVAL", 12L, 2600);

        verify(chargeService, times(1)).charge(argThat(command ->
                command.featureType().equals("INTERVIEW_ANSWER_EVAL")
                        && command.aiUsageLogId().equals(11L)
                        && command.tokenUsage().equals(2400)
                        && command.policyAcknowledgementKey().equals("ack-1")));
    }

    @Test
    void skipsBackgroundRequestWithoutAcknowledgementHeaders() {
        AiChargeService chargeService = mock(AiChargeService.class);
        AiChargeRequestSettlementService service = new AiChargeRequestSettlementService(chargeService);

        service.settleFirstAcknowledgedUsage(1L, "JOB_ANALYSIS", 10L, 1200);

        verifyNoInteractions(chargeService);
    }
}
