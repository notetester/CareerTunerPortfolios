package com.careertuner.support.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.support.domain.SupportTicket;
import com.careertuner.support.domain.TicketMessage;
import com.careertuner.support.dto.CreateTicketRequest;
import com.careertuner.support.dto.TicketResponse;
import com.careertuner.support.mapper.TicketMapper;
import com.careertuner.support.mapper.TicketMessageMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    private final TicketMapper ticketMapper;
    private final TicketMessageMapper messageMapper;

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, Long userId) {
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

    private TicketResponse toResponse(SupportTicket ticket) {
        return new TicketResponse(
                ticket.getId(), ticket.getSubject(), ticket.getCategory(),
                ticket.getStatus(), ticket.getCreatedAt(), null, null);
    }
}
