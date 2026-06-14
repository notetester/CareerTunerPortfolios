package com.careertuner.admin.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketListResponse {

    private Long id;
    private String category;
    private String subject;
    private String memberName;
    private String createdAt;
    private String status;
    private boolean priority;
    private String plan;
    private String joinedAt;
}
