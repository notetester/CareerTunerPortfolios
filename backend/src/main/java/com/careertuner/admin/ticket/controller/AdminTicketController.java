package com.careertuner.admin.ticket.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.admin.ticket.dto.AdminTicketDetailResponse;
import com.careertuner.admin.ticket.dto.AdminTicketListResponse;
import com.careertuner.admin.ticket.dto.AdminTicketReplyRequest;
import com.careertuner.admin.ticket.dto.AdminTicketUpdateRequest;
import com.careertuner.admin.ticket.service.AdminTicketService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final AdminTicketService ticketService;

    @GetMapping
    public ApiResponse<List<AdminTicketListResponse>> getTickets(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(ticketService.getTickets(authUser, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminTicketDetailResponse> getTicketDetail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        return ApiResponse.ok(ticketService.getTicketDetail(authUser, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminTicketDetailResponse> updateTicket(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody AdminTicketUpdateRequest request) {
        return ApiResponse.ok(ticketService.updateTicket(authUser, id, request));
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<AdminTicketDetailResponse> reply(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @RequestBody AdminTicketReplyRequest request) {
        return ApiResponse.ok(ticketService.reply(authUser, id, request));
    }
}
