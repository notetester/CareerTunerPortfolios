package com.careertuner.admin.ticket.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketDetailResponse {

    private Long id;
    private String category;
    private String subject;
    private String memberName;
    private String createdAt;
    private String status;
    private boolean priority;
    private String plan;
    private String joinedAt;
    private String memo;
    private List<AdminTicketMessageResponse> msgs;
}
