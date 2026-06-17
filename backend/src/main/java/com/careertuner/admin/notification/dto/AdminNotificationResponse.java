package com.careertuner.admin.notification.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationResponse {

    private Long id;
    private Long userId;
    private String recipientName;
    private String recipientEmail;
    private String type;
    private String title;
    private String message;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
