package com.careertuner.support.service;

import java.util.List;

import com.careertuner.support.dto.CreateTicketRequest;
import com.careertuner.support.dto.TicketResponse;
import com.careertuner.support.dto.TicketThreadResponse;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request, Long userId);

    TicketResponse getTicket(Long id, Long userId);

    List<TicketResponse> listMyTickets(Long userId);

    /** 문의 단건의 전체 대화(원문 + 답변 + 추가 문의). */
    TicketThreadResponse getThread(Long ticketId, Long userId);

    /** 사용자가 자신의 문의에 추가 메시지를 남긴다(상태는 재접수로 되돌림). */
    TicketThreadResponse addUserMessage(Long ticketId, Long userId, String content);
}
