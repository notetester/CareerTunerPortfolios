package com.careertuner.admin.ticket.dto;

public record AdminTicketUpdateRequest(
        String status,
        String priority
) {}
