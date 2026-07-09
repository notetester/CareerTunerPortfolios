package com.careertuner.admin.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careertuner.admin.notice.dto.AdminNoticeRequest;
import com.careertuner.admin.notice.dto.AdminNoticeResponse;
import com.careertuner.admin.notice.mapper.AdminNoticeMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

/**
 * 공지 예약(C) 백엔드 — create/update 의 scheduled_at 배선/가드 단위 검증.
 * status=SCHEDULED 일 때만 예약 시각을 저장하고, 다른 상태로 바뀌면 비우며,
 * 시각 누락은 거부, 수정 시 미전송이면 기존 시각을 보존한다.
 */
class AdminNoticeServiceImplTest {

    private final AdminNoticeMapper mapper = mock(AdminNoticeMapper.class);
    private final AdminNoticeServiceImpl service = new AdminNoticeServiceImpl(mapper);

    private final AuthUser admin = new AuthUser(1L, "admin@careertuner.dev", "ADMIN");

    private AdminNoticeRequest req(String status, String scheduledAt) {
        return new AdminNoticeRequest("제목", "본문", status, false, "GENERAL", null, scheduledAt);
    }

    // ── 생성: SCHEDULED + 시각 → 파싱된 예약 시각 저장, published_at 미설정 ──
    @Test
    void create_scheduledWithTime_persistsScheduledAt() {
        when(mapper.lastInsertId()).thenReturn(10L);
        when(mapper.findById(10L)).thenReturn(new AdminNoticeResponse());

        service.createNotice(admin, req("SCHEDULED", "2026-07-01T09:00"));

        ArgumentCaptor<LocalDateTime> cap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).insert(eq("제목"), eq("본문"), eq("GENERAL"), eq("SCHEDULED"),
                eq(false), isNull(), eq(1L), cap.capture(), eq(false)); // setPublishedAt=false
        assertThat(cap.getValue()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    // ── 생성: SCHEDULED 인데 시각 없음 → 거부, insert 미호출 ──
    @Test
    void create_scheduledWithoutTime_isRejected() {
        assertThatThrownBy(() -> service.createNotice(admin, req("SCHEDULED", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);

        verify(mapper, never()).insert(any(), any(), any(), any(), eq(false), any(), any(), any(), eq(false));
    }

    // ── 생성: PUBLISHED → 예약 시각 NULL, published_at 설정(setPublishedAt=true) ──
    @Test
    void create_published_hasNullScheduledAt_andSetsPublishedAt() {
        when(mapper.lastInsertId()).thenReturn(11L);
        when(mapper.findById(11L)).thenReturn(new AdminNoticeResponse());

        service.createNotice(admin, req("PUBLISHED", null));

        verify(mapper).insert(eq("제목"), eq("본문"), eq("GENERAL"), eq("PUBLISHED"),
                eq(false), isNull(), eq(1L), isNull(), eq(true)); // scheduledAt=null, setPublishedAt=true
    }

    // ── 생성: 잘못된 시각 형식 → 거부 ──
    @Test
    void create_scheduledWithBadTime_isRejected() {
        assertThatThrownBy(() -> service.createNotice(admin, req("SCHEDULED", "2026/07/01 09:00")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ── 수정: SCHEDULED 유지 + 시각 미전송 → 기존 예약 시각 보존 ──
    @Test
    void update_keepsScheduled_preservesExistingTime() {
        LocalDateTime existingAt = LocalDateTime.of(2026, 8, 1, 0, 0);
        AdminNoticeResponse existing = AdminNoticeResponse.builder()
                .id(5L).title("o").content("o").category("GENERAL").status("SCHEDULED")
                .pinned(false).scheduledAt(existingAt).publishedAt(null).build();
        when(mapper.findById(5L)).thenReturn(existing);

        // status·scheduledAt 미전송(고정 토글 같은 부분수정) → 상태/시각 보존
        service.updateNotice(admin, 5L, new AdminNoticeRequest(null, null, null, true, null, null, null));

        ArgumentCaptor<LocalDateTime> cap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).update(eq(5L), any(), any(), any(), eq("SCHEDULED"),
                eq(true), any(), cap.capture(), eq(false));
        assertThat(cap.getValue()).isEqualTo(existingAt); // 기존 시각 보존
    }

    // ── 수정: SCHEDULED → PUBLISHED 전환 → 예약 시각 비움 + published_at 설정 ──
    @Test
    void update_switchToPublished_clearsScheduledAt() {
        AdminNoticeResponse existing = AdminNoticeResponse.builder()
                .id(5L).title("o").content("o").category("GENERAL").status("SCHEDULED")
                .pinned(false).scheduledAt(LocalDateTime.of(2026, 8, 1, 0, 0)).publishedAt(null).build();
        when(mapper.findById(5L)).thenReturn(existing);

        service.updateNotice(admin, 5L, new AdminNoticeRequest(null, null, "PUBLISHED", null, null, null, null));

        verify(mapper).update(eq(5L), any(), any(), any(), eq("PUBLISHED"),
                eq(false), any(), isNull(), eq(true)); // scheduledAt=null, setPublishedAt=true
    }
}
