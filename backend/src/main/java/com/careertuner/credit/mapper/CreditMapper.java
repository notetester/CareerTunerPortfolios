package com.careertuner.credit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.credit.domain.CreditAiUsageLog;
import com.careertuner.credit.domain.CreditTransaction;

@Mapper
public interface CreditMapper {

    /** 차감 기준이 되는 AI 사용량 로그를 조회한다. */
    CreditAiUsageLog findAiUsageLogById(@Param("aiUsageLogId") Long aiUsageLogId);

    /** 같은 AI 사용량 로그가 이미 차감 원장에 반영됐는지 확인한다. */
    boolean existsTransactionByAiUsageLogIdAndType(@Param("aiUsageLogId") Long aiUsageLogId,
                                                   @Param("type") String type);

    /** 호출 파트가 차감 금액을 직접 지정한 경우 사용량 로그의 집계 금액도 맞춘다. */
    int updateAiUsageCreditUsed(@Param("aiUsageLogId") Long aiUsageLogId,
                                @Param("creditUsed") int creditUsed);

    /** 잔액이 충분할 때만 사용자 크레딧을 실제 차감한다. */
    int deductUserCreditIfEnough(@Param("userId") Long userId,
                                 @Param("creditUsed") int creditUsed);

    /** 리워드/보너스 등으로 크레딧을 적립한다. 오버플로 방지 가드 포함. 성공 시 1. */
    int addUserCredit(@Param("userId") Long userId, @Param("amount") int amount);

    /** 차감 전후 잔액 확인용 사용자 크레딧을 조회한다. */
    Integer findUserCredit(@Param("userId") Long userId);

    /** 크레딧 변동 원장을 기록한다. */
    void insertCreditTransaction(CreditTransaction transaction);
}
