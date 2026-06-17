package com.careertuner.support.service;

import java.util.List;

import com.careertuner.support.dto.CreateTicketRequest;
import com.careertuner.support.dto.TicketResponse;

public interface TicketService {

    TicketResponse createTicket(CreateTicketRequest request, Long userId);

    TicketResponse getTicket(Long id, Long userId);

    List<TicketResponse> listMyTickets(Long userId);
}
