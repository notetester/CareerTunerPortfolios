package com.careertuner.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.credit.domain.CreditAiUsageLog;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.credit.dto.CreditDeductionResult;
import com.careertuner.credit.mapper.CreditMapper;

class CreditServiceImplTest {

    private final CreditMapper creditMapper = org.mockito.Mockito.mock(CreditMapper.class);
    private final CreditServiceImpl service = new CreditServiceImpl(creditMapper);

    @Test
    void deductByAiUsageLogDeductsUserCreditAndWritesLedger() {
        CreditAiUsageLog usageLog = usageLog(100L, 1L, "JOB_ANALYSIS", "SUCCESS", 3);
        when(creditMapper.findAiUsageLogById(100L)).thenReturn(usageLog);
        when(creditMapper.findUserCredit(1L)).thenReturn(10, 7);
        when(creditMapper.existsTransactionByAiUsageLogIdAndType(100L, "AI_USAGE")).thenReturn(false);
        when(creditMapper.deductUserCreditIfEnough(1L, 3)).thenReturn(1);

        CreditDeductionResult result = service.deductByAiUsageLog(100L);

        assertThat(result.deducted()).isTrue();
        assertThat(result.creditUsed()).isEqualTo(3);
        assertThat(result.balanceAfter()).isEqualTo(7);
        verify(creditMapper).deductUserCreditIfEnough(1L, 3);

        ArgumentCaptor<CreditTransaction> captor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditMapper).insertCreditTransaction(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getAiUsageLogId()).isEqualTo(100L);
        assertThat(captor.getValue().getType()).isEqualTo("AI_USAGE");
        assertThat(captor.getValue().getAmount()).isEqualTo(-3);
        assertThat(captor.getValue().getFeatureType()).isEqualTo("JOB_ANALYSIS");
    }

    @Test
    void explicitCreditAmountOverridesUsageLogBeforeDeduction() {
        CreditAiUsageLog usageLog = usageLog(101L, 2L, "CORRECTION_ANSWER", "SUCCESS", 1);
        when(creditMapper.findAiUsageLogById(101L)).thenReturn(usageLog);
        when(creditMapper.findUserCredit(2L)).thenReturn(5, 3);
        when(creditMapper.existsTransactionByAiUsageLogIdAndType(101L, "AI_USAGE")).thenReturn(false);
        when(creditMapper.deductUserCreditIfEnough(2L, 2)).thenReturn(1);

        CreditDeductionResult result = service.deductByAiUsageLog(101L, 2);

        assertThat(result.deducted()).isTrue();
        assertThat(result.creditUsed()).isEqualTo(2);
        verify(creditMapper).updateAiUsageCreditUsed(101L, 2);
        verify(creditMapper).deductUserCreditIfEnough(2L, 2);
    }

    @Test
    void alreadyDeductedUsageLogDoesNotDeductAgain() {
        CreditAiUsageLog usageLog = usageLog(102L, 3L, "INTERVIEW_REPORT", "SUCCESS", 4);
        when(creditMapper.findAiUsageLogById(102L)).thenReturn(usageLog);
        when(creditMapper.findUserCredit(3L)).thenReturn(8);
        when(creditMapper.existsTransactionByAiUsageLogIdAndType(102L, "AI_USAGE")).thenReturn(true);

        CreditDeductionResult result = service.deductByAiUsageLog(102L);

        assertThat(result.deducted()).isFalse();
        assertThat(result.reason()).isEqualTo("ALREADY_DEDUCTED");
        verify(creditMapper, never()).deductUserCreditIfEnough(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyInt());
        verify(creditMapper, never()).insertCreditTransaction(org.mockito.Mockito.any());
    }

    @Test
    void failedUsageLogDoesNotDeductCredit() {
        CreditAiUsageLog usageLog = usageLog(103L, 4L, "COMPANY_RESEARCH", "FAILED", 2);
        when(creditMapper.findAiUsageLogById(103L)).thenReturn(usageLog);
        when(creditMapper.findUserCredit(4L)).thenReturn(6);

        CreditDeductionResult result = service.deductByAiUsageLog(103L);

        assertThat(result.deducted()).isFalse();
        assertThat(result.reason()).isEqualTo("NOT_SUCCESS");
        verify(creditMapper, never()).deductUserCreditIfEnough(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyInt());
    }

    @Test
    void insufficientCreditThrowsPaymentRequiredAndDoesNotWriteLedger() {
        CreditAiUsageLog usageLog = usageLog(104L, 5L, "FIT_ANALYSIS", "SUCCESS", 9);
        when(creditMapper.findAiUsageLogById(104L)).thenReturn(usageLog);
        when(creditMapper.findUserCredit(5L)).thenReturn(3);
        when(creditMapper.existsTransactionByAiUsageLogIdAndType(104L, "AI_USAGE")).thenReturn(false);
        when(creditMapper.deductUserCreditIfEnough(5L, 9)).thenReturn(0);

        assertThatThrownBy(() -> service.deductByAiUsageLog(104L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_CREDIT);

        verify(creditMapper, never()).insertCreditTransaction(org.mockito.Mockito.any());
    }

    @Test
    void negativeExplicitCreditAmountIsRejected() {
        assertThatThrownBy(() -> service.deductByAiUsageLog(105L, -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static CreditAiUsageLog usageLog(Long id, Long userId, String featureType, String status, int creditUsed) {
        CreditAiUsageLog usageLog = new CreditAiUsageLog();
        usageLog.setId(id);
        usageLog.setUserId(userId);
        usageLog.setFeatureType(featureType);
        usageLog.setStatus(status);
        usageLog.setCreditUsed(creditUsed);
        return usageLog;
    }
}
