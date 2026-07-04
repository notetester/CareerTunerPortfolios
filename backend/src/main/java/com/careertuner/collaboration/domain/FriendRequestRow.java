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
public class FriendRequestRow {

    private Long id;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private Long requesterId;
    private String requesterName;
    private String requesterEmail;
    private Long receiverId;
    private String receiverName;
    private String receiverEmail;
}
