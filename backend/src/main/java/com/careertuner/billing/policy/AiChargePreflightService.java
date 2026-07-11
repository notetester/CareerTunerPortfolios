package com.careertuner.billing.policy;

import org.springframework.stereotype.Service;

import com.careertuner.billing.dto.AiChargePreviewRequest;
import com.careertuner.billing.dto.AiChargePreviewResponse;
import com.careertuner.billing.service.AiChargePreviewService;
import com.careertuner.billing.service.RefundPolicyService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 고비용 작업을 시작하기 전에 잔여 사용권/크레딧과 정책 확인을 검증한다. */
@Service
@RequiredArgsConstructor
public class AiChargePreflightService {

    private final AiChargePreviewService previewService;
    private final RefundPolicyService refundPolicyService;

    public AiChargePreviewResponse requireAcknowledged(
            Long userId, String featureType, String acknowledgementKey) {
        AiChargePreviewResponse preview = previewService.preview(
                userId, new AiChargePreviewRequest(featureType, null, acknowledgementKey));
        if ("BLOCKED".equals(preview.chargeType()) || !preview.sufficient()) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_CREDIT,
                    "사용 가능한 사용권 또는 크레딧이 부족합니다.");
        }
        if (preview.triggerType() != null && !preview.triggerType().isBlank()) {
            refundPolicyService.requireUsageAcknowledgement(
                    userId, preview.triggerType(), preview.actionKey());
        }
        return preview;
    }
}
