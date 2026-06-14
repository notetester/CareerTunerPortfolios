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
public class SupportTicket {

    private Long id;
    private Long userId;
    private String subject;
    private String category;
    private String status;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
