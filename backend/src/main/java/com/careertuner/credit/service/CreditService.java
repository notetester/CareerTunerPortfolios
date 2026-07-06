package com.careertuner.credit.service;

import com.careertuner.credit.dto.CreditDeductionResult;

/** AI 사용량 로그를 기준으로 사용자 크레딧을 실제 차감하는 공통 서비스. */
public interface CreditService {

    /** ai_usage_log.credit_used 값만큼 차감한다. */
    CreditDeductionResult deductByAiUsageLog(Long aiUsageLogId);

    /** 호출 파트가 지정한 금액으로 ai_usage_log.credit_used를 맞춘 뒤 차감한다. */
    CreditDeductionResult deductByAiUsageLog(Long aiUsageLogId, int creditUsed);

    /**
     * 리워드/보너스/쿠폰 등으로 크레딧을 적립하고 변동 원장(credit_transaction)을 남긴다.
     * AI 사용량 차감과 동일한 원장/잔액 메커니즘을 재사용한다. 반환값은 적립 후 잔액.
     */
    int grantCredit(Long userId, int amount, String type, String featureType, String reason);
}
