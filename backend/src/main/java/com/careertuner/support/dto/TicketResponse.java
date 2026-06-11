package com.careertuner.support.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TicketResponse(
        Long id,
        String subject,
        String category,
        String status,
        LocalDateTime createdAt,
        String reply,
        LocalDateTime repliedAt
) {}
