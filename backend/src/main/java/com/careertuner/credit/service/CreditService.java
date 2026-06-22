package com.careertuner.credit.service;

import com.careertuner.credit.dto.CreditDeductionResult;

/** AI 사용량 로그를 기준으로 사용자 크레딧을 실제 차감하는 공통 서비스. */
public interface CreditService {

    /** ai_usage_log.credit_used 값만큼 차감한다. */
    CreditDeductionResult deductByAiUsageLog(Long aiUsageLogId);

    /** 호출 파트가 지정한 금액으로 ai_usage_log.credit_used를 맞춘 뒤 차감한다. */
    CreditDeductionResult deductByAiUsageLog(Long aiUsageLogId, int creditUsed);
}
