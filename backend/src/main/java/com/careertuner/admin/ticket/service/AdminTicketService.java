package com.careertuner.admin.ticket.service;

import java.util.List;

import com.careertuner.admin.ticket.dto.AdminTicketDetailResponse;
import com.careertuner.admin.ticket.dto.AdminTicketListResponse;
import com.careertuner.admin.ticket.dto.AdminTicketReplyRequest;
import com.careertuner.admin.ticket.dto.AdminTicketUpdateRequest;
import com.careertuner.common.security.AuthUser;

public interface AdminTicketService {

    List<AdminTicketListResponse> getTickets(AuthUser authUser, String status);

    AdminTicketDetailResponse getTicketDetail(AuthUser authUser, Long id);

    AdminTicketDetailResponse updateTicket(AuthUser authUser, Long id, AdminTicketUpdateRequest request);

    AdminTicketDetailResponse reply(AuthUser authUser, Long id, AdminTicketReplyRequest request);
}
