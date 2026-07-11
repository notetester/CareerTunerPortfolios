package com.careertuner.admin.credit.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.credit.dto.AdminCreditSearchCriteria;
import com.careertuner.admin.credit.dto.AdminCreditSummary;
import com.careertuner.admin.credit.dto.AdminCreditTransactionRow;
import com.careertuner.admin.credit.dto.AdminCreditUserBalance;
import com.careertuner.credit.domain.CreditTransaction;

@Mapper
public interface AdminCreditMapper {
    List<AdminCreditTransactionRow> findTransactions(
            @Param("criteria") AdminCreditSearchCriteria criteria);

    long countTransactions(@Param("criteria") AdminCreditSearchCriteria criteria);

    AdminCreditSummary findSummary();

    AdminCreditUserBalance findUserBalanceForUpdate(@Param("userId") Long userId);

    CreditTransaction findAdminAdjustmentByRequestKey(
            @Param("userId") Long userId,
            @Param("requestKey") String requestKey);

    void insertAdminAdjustment(CreditTransaction transaction);

    int addUserCredit(@Param("userId") Long userId, @Param("amount") int amount);

    int deductUserCreditIfEnough(@Param("userId") Long userId, @Param("amount") int amount);
}
