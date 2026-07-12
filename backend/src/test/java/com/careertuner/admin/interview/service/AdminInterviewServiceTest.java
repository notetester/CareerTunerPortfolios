package com.careertuner.admin.interview.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careertuner.admin.interview.dto.AdminInterviewSessionRow;
import com.careertuner.admin.interview.mapper.AdminInterviewMapper;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.interview.mapper.InterviewMapper;
import com.careertuner.interview.media.InterviewMediaService;

class AdminInterviewServiceTest {

    private final AdminInterviewMapper adminInterviewMapper = mock(AdminInterviewMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final InterviewMediaService mediaService = mock(InterviewMediaService.class);
    private final AdminActionLogService actionLogService = mock(AdminActionLogService.class);
    private final AdminInterviewService service = new AdminInterviewService(
            adminInterviewMapper, interviewMapper, mediaService, actionLogService);

    @Test
    void updateMemoNormalizesValueAndWritesBeforeAfterAudit() {
        AdminInterviewSessionRow session = session(10L, 20L);
        when(adminInterviewMapper.findSession(10L)).thenReturn(session);
        when(adminInterviewMapper.findAdminMemoForUpdate(10L)).thenReturn("이전 메모");
        when(adminInterviewMapper.updateAdminMemo(10L, "새 메모")).thenReturn(1);

        service.updateMemo(admin(), 10L, "  새 메모  ");

        verify(adminInterviewMapper).updateAdminMemo(10L, "새 메모");
        verify(actionLogService).record(
                admin(), 20L, "INTERVIEW_MEMO_UPDATED", "INTERVIEW_SESSION",
                "이전 메모", "새 메모", "면접 운영 메모 수정");
    }

    @Test
    void updateMemoConvertsBlankToNull() {
        when(adminInterviewMapper.findSession(10L)).thenReturn(session(10L, 20L));
        when(adminInterviewMapper.findAdminMemoForUpdate(10L)).thenReturn("이전 메모");
        when(adminInterviewMapper.updateAdminMemo(10L, null)).thenReturn(1);

        service.updateMemo(admin(), 10L, "   ");

        verify(adminInterviewMapper).updateAdminMemo(10L, null);
        verify(actionLogService).record(
                admin(), 20L, "INTERVIEW_MEMO_UPDATED", "INTERVIEW_SESSION",
                "이전 메모", null, "면접 운영 메모 수정");
    }

    @Test
    void updateMemoRejectsMissingOrDeletedSessionWithoutAudit() {
        when(adminInterviewMapper.findSession(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateMemo(admin(), 99L, "메모"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(adminInterviewMapper, never()).updateAdminMemo(99L, "메모");
        verify(actionLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateMemoDoesNotAuditWhenGuardedUpdateLosesRace() {
        when(adminInterviewMapper.findSession(10L)).thenReturn(session(10L, 20L));
        when(adminInterviewMapper.findAdminMemoForUpdate(10L)).thenReturn("이전 메모");
        when(adminInterviewMapper.updateAdminMemo(10L, "새 메모")).thenReturn(0);

        assertThatThrownBy(() -> service.updateMemo(admin(), 10L, "새 메모"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(actionLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    private static AdminInterviewSessionRow session(Long id, Long userId) {
        AdminInterviewSessionRow session = new AdminInterviewSessionRow();
        session.setId(id);
        session.setUserId(userId);
        return session;
    }

    private static AuthUser admin() {
        return new AuthUser(1L, "admin@example.com", "ADMIN");
    }
}
