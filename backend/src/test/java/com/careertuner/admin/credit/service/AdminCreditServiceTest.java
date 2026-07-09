package com.careertuner.admin.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.credit.dto.AdminCreditAdjustRequest;
import com.careertuner.admin.credit.dto.AdminCreditAdjustResponse;
import com.careertuner.admin.credit.dto.AdminCreditSearchCriteria;
import com.careertuner.admin.credit.dto.AdminCreditUserBalance;
import com.careertuner.admin.credit.mapper.AdminCreditMapper;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.credit.domain.CreditTransaction;
import com.careertuner.credit.mapper.CreditMapper;

class AdminCreditServiceTest {

    private final AdminCreditMapper mapper = mock(AdminCreditMapper.class);
    private final CreditMapper creditMapper = mock(CreditMapper.class);
    private final AdminActionLogService actionLogService = mock(AdminActionLogService.class);
    private final AdminCreditService service = new AdminCreditService(mapper, creditMapper, actionLogService);

    @Test
    void transactionsNormalizesFiltersAndPaging() {
        when(mapper.findTransactions(any())).thenReturn(List.of());

        service.transactions(admin(), " user@example.com ", 10L, "admin_adjust", 0, 500);

        ArgumentCaptor<AdminCreditSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminCreditSearchCriteria.class);
        verify(mapper).findTransactions(captor.capture());
        AdminCreditSearchCriteria criteria = captor.getValue();
        assertThat(criteria.keyword()).isEqualTo("user@example.com");
        assertThat(criteria.userId()).isEqualTo(10L);
        assertThat(criteria.type()).isEqualTo("ADMIN_ADJUST");
        assertThat(criteria.page()).isEqualTo(1);
        assertThat(criteria.size()).isEqualTo(100);
        assertThat(criteria.offset()).isZero();
        verify(mapper).countTransactions(criteria);
    }

    @Test
    void transactionsRejectsUnknownType() {
        assertThatThrownBy(() -> service.transactions(admin(), null, null, "GIFT", 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(mapper, never()).findTransactions(any());
    }

    @Test
    void transactionsAllowsOmittedUserFilter() {
        when(mapper.findTransactions(any())).thenReturn(List.of());

        service.transactions(admin(), null, null, null, 1, 20);

        ArgumentCaptor<AdminCreditSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminCreditSearchCriteria.class);
        verify(mapper).findTransactions(captor.capture());
        assertThat(captor.getValue().userId()).isNull();
    }

    @Test
    void positiveAdjustmentUpdatesBalanceAndWritesBothLedgers() {
        when(mapper.findUserBalanceForUpdate(10L)).thenReturn(user(10L, 30));
        when(mapper.addUserCredit(10L, 5)).thenReturn(1);
        when(creditMapper.findUserCredit(10L)).thenReturn(35);
        doAnswer(invocation -> {
            invocation.getArgument(0, CreditTransaction.class).setId(100L);
            return null;
        }).when(creditMapper).insertCreditTransaction(any());

        AdminCreditAdjustResponse response = service.adjust(
                admin(), new AdminCreditAdjustRequest(10L, 5, " 고객 보상 "));

        assertThat(response.transactionId()).isEqualTo(100L);
        assertThat(response.balanceBefore()).isEqualTo(30);
        assertThat(response.balanceAfter()).isEqualTo(35);

        ArgumentCaptor<CreditTransaction> transactionCaptor =
                ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditMapper).insertCreditTransaction(transactionCaptor.capture());
        CreditTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getType()).isEqualTo("ADMIN_ADJUST");
        assertThat(transaction.getAmount()).isEqualTo(5);
        assertThat(transaction.getBalanceAfter()).isEqualTo(35);
        assertThat(transaction.getFeatureType()).isEqualTo("ADMIN_CREDIT_ADJUST");
        assertThat(transaction.getReason()).isEqualTo("고객 보상");
        verify(actionLogService).record(
                admin(), 10L, "CREDIT_ADJUSTED", "CREDIT", "30", "35", "고객 보상");
    }

    @Test
    void negativeAdjustmentUsesGuardedDeduction() {
        when(mapper.findUserBalanceForUpdate(10L)).thenReturn(user(10L, 30));
        when(creditMapper.deductUserCreditIfEnough(10L, 7)).thenReturn(1);
        when(creditMapper.findUserCredit(10L)).thenReturn(23);

        AdminCreditAdjustResponse response = service.adjust(
                admin(), new AdminCreditAdjustRequest(10L, -7, "오지급 회수"));

        assertThat(response.amount()).isEqualTo(-7);
        assertThat(response.balanceAfter()).isEqualTo(23);
        verify(creditMapper).deductUserCreditIfEnough(10L, 7);
        verify(mapper, never()).addUserCredit(any(), any(Integer.class));
    }

    @Test
    void insufficientBalanceDoesNotWriteLedger() {
        when(mapper.findUserBalanceForUpdate(10L)).thenReturn(user(10L, 3));
        when(creditMapper.deductUserCreditIfEnough(10L, 7)).thenReturn(0);

        assertThatThrownBy(() -> service.adjust(
                admin(), new AdminCreditAdjustRequest(10L, -7, "오지급 회수")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_CREDIT);

        verify(creditMapper, never()).insertCreditTransaction(any());
        verify(actionLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void zeroAndOversizedAdjustmentsAreRejectedBeforeLock() {
        assertThatThrownBy(() -> service.adjust(
                admin(), new AdminCreditAdjustRequest(10L, 0, "테스트")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> service.adjust(
                admin(), new AdminCreditAdjustRequest(10L, 1_000_001, "테스트")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(mapper, never()).findUserBalanceForUpdate(any());
    }

    @Test
    void adjustmentRejectsMissingUser() {
        when(mapper.findUserBalanceForUpdate(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.adjust(
                admin(), new AdminCreditAdjustRequest(99L, 1, "테스트")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static AdminCreditUserBalance user(Long id, int credit) {
        AdminCreditUserBalance user = new AdminCreditUserBalance();
        user.setId(id);
        user.setCredit(credit);
        return user;
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
