package com.careertuner.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.careertuner.billing.dto.AiChargeCommand;
import com.careertuner.billing.dto.AiChargeResult;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 결제 고지를 확인한 HTTP 요청의 첫 번째 성공 AI 사용 로그만 정산한다. */
@Service
@RequiredArgsConstructor
public class AiChargeRequestSettlementService {

    public static final String ACKNOWLEDGEMENT_HEADER = "X-AI-Charge-Acknowledgement";
    public static final String FEATURE_HEADER = "X-AI-Charge-Feature";
    private static final String SETTLED_ATTRIBUTE = AiChargeRequestSettlementService.class.getName() + ".settled";

    private final AiChargeService aiChargeService;

    public AiChargeResult settleFirstAcknowledgedUsage(Long userId,
                                                       String featureType,
                                                       Long aiUsageLogId,
                                                       Integer tokenUsage) {
        ServletRequestAttributes attributes = currentRequestAttributes();
        if (attributes == null || aiUsageLogId == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String acknowledgementKey = request.getHeader(ACKNOWLEDGEMENT_HEADER);
        String acknowledgedFeature = request.getHeader(FEATURE_HEADER);
        if (acknowledgementKey == null || acknowledgementKey.isBlank()
                || !featureType.equals(acknowledgedFeature)
                || Boolean.TRUE.equals(attributes.getAttribute(SETTLED_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST))) {
            return null;
        }

        AiChargeResult result = aiChargeService.charge(new AiChargeCommand(
                userId,
                featureType,
                "AI_USAGE_LOG",
                aiUsageLogId,
                aiUsageLogId,
                null,
                tokenUsage,
                featureType + " usage",
                acknowledgementKey));
        attributes.setAttribute(SETTLED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        return result;
    }

    private ServletRequestAttributes currentRequestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet : null;
    }
}
