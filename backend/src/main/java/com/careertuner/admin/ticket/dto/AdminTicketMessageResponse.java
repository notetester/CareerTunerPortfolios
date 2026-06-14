package com.careertuner.admin.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketMessageResponse {

    private Long id;
    private String who;
    private String name;
    private String time;
    private String text;
    private boolean internal;
}
