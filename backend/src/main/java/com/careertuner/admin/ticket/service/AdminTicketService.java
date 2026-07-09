package com.careertuner.admin.ticket.service;

import java.util.List;

import com.careertuner.admin.ticket.dto.AdminTicketDetailResponse;
import com.careertuner.admin.ticket.dto.AdminTicketDraftResponse;
import com.careertuner.admin.ticket.dto.AdminTicketListResponse;
import com.careertuner.admin.ticket.dto.AdminTicketSummaryResponse;
import com.careertuner.admin.ticket.dto.AdminTicketReplyRequest;
import com.careertuner.admin.ticket.dto.AdminTicketUpdateRequest;
import com.careertuner.common.security.AuthUser;
import com.careertuner.file.service.FileService;

public interface AdminTicketService {

    List<AdminTicketListResponse> getTickets(AuthUser authUser, String status);

    AdminTicketDetailResponse getTicketDetail(AuthUser authUser, Long id);

    AdminTicketDetailResponse updateTicket(AuthUser authUser, Long id, AdminTicketUpdateRequest request);

    AdminTicketDetailResponse reply(AuthUser authUser, Long id, AdminTicketReplyRequest request);

    AdminTicketDraftResponse generateDraft(AuthUser authUser, Long id);

    AdminTicketSummaryResponse generateMemberSummary(AuthUser authUser, Long id);

    /** 상담사가 티켓 첨부 파일을 내려받는다(티켓 첨부인지 검증 후 소유자 무관 다운로드). */
    FileService.Download downloadAttachment(AuthUser authUser, Long fileId);
}
