package com.careertuner.support.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.file.service.FileService;
import com.careertuner.support.dto.CreateTicketRequest;
import com.careertuner.support.mapper.TicketMapper;
import com.careertuner.support.mapper.TicketMessageMapper;

/**
 * 문의 작성 rate-limit <b>토글 검증</b>(사용자 요구: OFF 무제약, ON 임계 집행, 값 변경 반영).
 * 차단은 예외 발생 + ticketMapper.insert 미호출로 판정한다.
 */
class TicketServiceImplTest {

    private final TicketMapper ticketMapper = mock(TicketMapper.class);
    private final TicketMessageMapper messageMapper = mock(TicketMessageMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ModerationSettingService moderationSettingService = mock(ModerationSettingService.class);
    private final FileService fileService = mock(FileService.class);

    private final TicketServiceImpl service =
            new TicketServiceImpl(ticketMapper, messageMapper, eventPublisher, moderationSettingService, fileService);

    private static final CreateTicketRequest REQ = new CreateTicketRequest("기타", "제목", "본문 내용", null);
    private static final long USER = 7L;

    // ── OFF(inquiry max=0): 무제약 — 사용량 조회조차 안 하고 통과 ──
    @Test
    void off_unlimited_neverCountsNorBlocks() {
        when(moderationSettingService.getInquiryRateMax()).thenReturn(0);
        assertThatCode(() -> service.createTicket(REQ, USER)).doesNotThrowAnyException();
        verify(ticketMapper).insert(any());
        verify(ticketMapper, never()).countRecentByUser(anyLong(), any());
    }

    // ── ON(max=3): 임계 미만이면 통과 ──
    @Test
    void on_belowLimit_allows() {
        when(moderationSettingService.getInquiryRateMax()).thenReturn(3);
        when(moderationSettingService.getInquiryRateWindowSeconds()).thenReturn(600);
        when(ticketMapper.countRecentByUser(anyLong(), any())).thenReturn(2); // 2 + 이번 = 3? 아니 <3 → 통과
        assertThatCode(() -> service.createTicket(REQ, USER)).doesNotThrowAnyException();
        verify(ticketMapper).insert(any());
    }

    // ── ON(max=3): 임계 도달이면 429 차단, 저장 안 함 ──
    @Test
    void on_atLimit_blocks() {
        when(moderationSettingService.getInquiryRateMax()).thenReturn(3);
        when(moderationSettingService.getInquiryRateWindowSeconds()).thenReturn(600);
        when(ticketMapper.countRecentByUser(anyLong(), any())).thenReturn(3);
        assertThatThrownBy(() -> service.createTicket(REQ, USER)).isInstanceOf(BusinessException.class);
        verify(ticketMapper, never()).insert(any());
    }

    // ── ON 변경(max=1): 값 변경이 의도대로 — 1건만 있어도 차단 ──
    @Test
    void on_custom1_blocksAtOne() {
        when(moderationSettingService.getInquiryRateMax()).thenReturn(1);
        when(moderationSettingService.getInquiryRateWindowSeconds()).thenReturn(600);
        when(ticketMapper.countRecentByUser(anyLong(), any())).thenReturn(1);
        assertThatThrownBy(() -> service.createTicket(REQ, USER)).isInstanceOf(BusinessException.class);
        verify(ticketMapper, never()).insert(any());
    }
}
