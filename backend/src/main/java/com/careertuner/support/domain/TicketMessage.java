package com.careertuner.support.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessage {

    private Long id;
    private Long ticketId;
    private String senderType;
    private Long senderId;
    private String content;
    private boolean internal;
    private LocalDateTime createdAt;
}
