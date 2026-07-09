package com.careertuner.admin.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminTicketReplyRequest(
        @NotBlank
        @Size(max = 4000)
        String content,
        Boolean internal
) {}
