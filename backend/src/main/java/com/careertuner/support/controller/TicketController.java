package com.careertuner.support.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;
import com.careertuner.support.dto.CreateTicketRequest;
import com.careertuner.support.dto.TicketResponse;
import com.careertuner.support.service.TicketService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/support/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public ApiResponse<TicketResponse> createTicket(
            @Validated @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(ticketService.createTicket(request, authUser.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketResponse> getTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(ticketService.getTicket(id, authUser.id()));
    }
}
