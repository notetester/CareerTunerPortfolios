package com.careertuner.support.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.moderation.service.ModerationSettingService;
import com.careertuner.file.service.FileService;
import com.careertuner.support.domain.SupportTicket;
import com.careertuner.support.domain.TicketMessage;
import com.careertuner.support.dto.CreateTicketRequest;
import com.careertuner.support.dto.TicketAttachmentView;
import com.careertuner.support.dto.TicketMessageView;
import com.careertuner.support.dto.TicketResponse;
import com.careertuner.support.dto.TicketThreadResponse;
import com.careertuner.support.event.NewTicketEvent;
import com.careertuner.support.mapper.TicketMapper;
import com.careertuner.support.mapper.TicketMessageMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;
    private final TicketMessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;
    /** 문의 작성 rate-limit(도배 방지) 정책값 — 검열/중재 정책 콘솔에서 런타임 편집. */
    private final ModerationSettingService moderationSettingService;
    private final FileService fileService;

    /** 첨부 파일을 support_ticket_message 에 연결할 때 쓰는 file_asset.ref_type 값. */
    private static final String ATTACHMENT_REF_TYPE = "SUPPORT_TICKET_MSG";
    /** 메시지 한 건당 첨부 최대 개수 — 프론트 안내(최대 5개)와 일치. */
    private static final int MAX_ATTACHMENTS = 5;

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, Long userId) {
        // 문의 작성 rate-limit(도배 방지) — 최근 window 초 안에 max 건 이상이면 429. (콘솔 편집값, 0=무제약)
        int inquiryRateMax = moderationSettingService.getInquiryRateMax();
        if (inquiryRateMax > 0 && userId != null) {
            int recent = ticketMapper.countRecentByUser(userId,
                    LocalDateTime.now().minusSeconds(moderationSettingService.getInquiryRateWindowSeconds()));
            if (recent >= inquiryRateMax) {
                throw new BusinessException(ErrorCode.RATE_LIMITED,
                        "짧은 시간에 너무 많은 문의를 남겼습니다. 잠시 후 다시 시도해 주세요.");
            }
        }

        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .subject(request.subject())
                .category(request.category())
                .status("RECEIVED")
                .priority("NORMAL")
                .build();
        ticketMapper.insert(ticket);

        TicketMessage message = TicketMessage.builder()
                .ticketId(ticket.getId())
                .senderType("USER")
                .senderId(userId)
                .content(request.content())
                .internal(false)
                .build();
        messageMapper.insert(message);

        // 첨부(있으면) — 업로드된 본인 파일을 이 메시지에 연결한다.
        fileService.linkOwnedFiles(userId, request.attachmentFileIds(),
                ATTACHMENT_REF_TYPE, message.getId(), MAX_ATTACHMENTS);

        // 관리자 알림(NEW_TICKET) — 커밋 후 AFTER_COMMIT 리스너에서 팬아웃(트랜잭션 분리)
        eventPublisher.publishEvent(new NewTicketEvent(ticket.getId(), ticket.getSubject()));

        return toResponse(ticket);
    }

    @Override
    public TicketResponse getTicket(Long id, Long userId) {
        SupportTicket ticket = ticketMapper.findByIdAndUserId(id, userId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        return withLatestReply(ticket);
    }

    @Override
    public List<TicketResponse> listMyTickets(Long userId) {
        return ticketMapper.findAllByUserId(userId).stream()
                .map(this::withLatestReply)
                .toList();
    }

    /** 티켓에 최신 관리자 답변(있으면)을 합쳐 응답으로 변환한다. */
    private TicketResponse withLatestReply(SupportTicket ticket) {
        TicketMessage adminReply = messageMapper.findByTicketId(ticket.getId()).stream()
                .filter(m -> "ADMIN".equals(m.getSenderType()))
                .reduce((a, b) -> b)
                .orElse(null);

        return new TicketResponse(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                adminReply != null ? adminReply.getContent() : null,
                adminReply != null ? adminReply.getCreatedAt() : null);
    }

    @Override
    public TicketThreadResponse getThread(Long ticketId, Long userId) {
        SupportTicket ticket = ticketMapper.findByIdAndUserId(ticketId, userId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        return toThread(ticket);
    }

    @Override
    @Transactional
    public TicketThreadResponse addUserMessage(Long ticketId, Long userId, String content, List<Long> attachmentFileIds) {
        SupportTicket ticket = ticketMapper.findByIdAndUserId(ticketId, userId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }
        // 종료된 문의는 사용자 메시지로 재오픈할 수 없다. 새 문의를 등록하게 한다.
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "종료된 문의에는 메시지를 추가할 수 없습니다. 새 문의를 등록해 주세요.");
        }
        TicketMessage message = TicketMessage.builder()
                .ticketId(ticketId)
                .senderType("USER")
                .senderId(userId)
                .content(content)
                .internal(false)
                .build();
        messageMapper.insert(message);

        // 첨부(있으면) — 업로드된 본인 파일을 이 메시지에 연결한다.
        fileService.linkOwnedFiles(userId, attachmentFileIds,
                ATTACHMENT_REF_TYPE, message.getId(), MAX_ATTACHMENTS);

        // 답변 완료/처리 중 상태에서만 사용자 추가 문의로 다시 접수 상태로 되돌려 관리자가 재확인하게 한다.
        // (CLOSED는 위에서 차단, RECEIVED는 이미 접수 상태라 변경 불필요)
        if ("ANSWERED".equals(ticket.getStatus()) || "IN_PROGRESS".equals(ticket.getStatus())) {
            ticketMapper.updateStatus(ticketId, "RECEIVED");
            ticket.setStatus("RECEIVED");
        }
        return toThread(ticket);
    }

    /** 내부 메모(is_internal)를 제외한 전체 대화를 시간순으로 묶는다. 메시지별 첨부도 함께 싣는다. */
    private TicketThreadResponse toThread(SupportTicket ticket) {
        List<TicketMessageView> messages = messageMapper.findByTicketId(ticket.getId()).stream()
                .filter(m -> !m.isInternal())
                .map(m -> TicketMessageView.from(m, attachmentsOf(m.getId())))
                .toList();
        return new TicketThreadResponse(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                messages);
    }

    private TicketResponse toResponse(SupportTicket ticket) {
        return new TicketResponse(
                ticket.getId(), ticket.getSubject(), ticket.getCategory(),
                ticket.getStatus(), ticket.getCreatedAt(), null, null);
    }

    /** 메시지에 연결된 첨부(file_asset ref)를 뷰로 변환한다. */
    private List<TicketAttachmentView> attachmentsOf(Long messageId) {
        return fileService.findLinkedFiles(ATTACHMENT_REF_TYPE, messageId).stream()
                .map(TicketAttachmentView::from)
                .toList();
    }
}
