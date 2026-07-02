package com.careertuner.collaboration.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationMessage {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String kind;
    private String content;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private String senderName;
    private String senderEmail;
}
