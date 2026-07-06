package com.careertuner.admin.correction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.correction.dto.AdminCorrectionDetail;
import com.careertuner.admin.correction.dto.AdminCorrectionSearchCriteria;
import com.careertuner.admin.correction.mapper.AdminCorrectionMapper;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

class AdminCorrectionServiceTest {

    private final AdminCorrectionMapper mapper = mock(AdminCorrectionMapper.class);
    private final AdminActionLogService actionLogService = mock(AdminActionLogService.class);
    private final AdminCorrectionService service = new AdminCorrectionService(mapper, actionLogService);

    @Test
    void correctionsNormalizesFiltersAndPaging() {
        when(mapper.findCorrections(any())).thenReturn(List.of());

        service.corrections(admin(), " user@example.com ", "resume", "success", "has_memo", 0, 500);

        ArgumentCaptor<AdminCorrectionSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminCorrectionSearchCriteria.class);
        verify(mapper).findCorrections(captor.capture());
        AdminCorrectionSearchCriteria criteria = captor.getValue();
        assertThat(criteria.keyword()).isEqualTo("user@example.com");
        assertThat(criteria.correctionType()).isEqualTo("RESUME");
        assertThat(criteria.status()).isEqualTo("SUCCESS");
        assertThat(criteria.memoState()).isEqualTo("HAS_MEMO");
        assertThat(criteria.page()).isEqualTo(1);
        assertThat(criteria.size()).isEqualTo(100);
        assertThat(criteria.offset()).isZero();
        verify(mapper).countCorrections(criteria);
    }

    @Test
    void correctionsRejectsUnknownType() {
        assertThatThrownBy(() -> service.corrections(
                admin(), null, "UNKNOWN", null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(mapper, never()).findCorrections(any());
    }

    @Test
    void aiFailuresCapsLimit() {
        when(mapper.findAiFailures(200)).thenReturn(List.of());

        service.aiFailures(admin(), 1000);

        verify(mapper).findAiFailures(200);
    }

    @Test
    void updateMemoTrimsAndWritesAuditLog() {
        AdminCorrectionDetail detail = new AdminCorrectionDetail();
        detail.setId(10L);
        detail.setUserId(20L);
        detail.setAdminMemo("이전 메모");
        when(mapper.findCorrection(10L)).thenReturn(detail);
        when(mapper.updateAdminMemo(10L, "새 메모")).thenReturn(1);

        service.updateMemo(admin(), 10L, "  새 메모  ");

        verify(mapper).updateAdminMemo(10L, "새 메모");
        verify(actionLogService).record(
                admin(), 20L, "CORRECTION_MEMO_UPDATED", "CORRECTION",
                "이전 메모", "새 메모", "첨삭 운영 메모 수정");
    }

    @Test
    void detailRejectsMissingCorrection() {
        when(mapper.findCorrection(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(admin(), 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
