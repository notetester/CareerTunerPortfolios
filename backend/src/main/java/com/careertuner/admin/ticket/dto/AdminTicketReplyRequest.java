package com.careertuner.admin.ticket.dto;

public record AdminTicketReplyRequest(
        String content,
        Boolean internal
) {}
