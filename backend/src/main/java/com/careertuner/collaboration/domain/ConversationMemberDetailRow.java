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
public class ConversationMemberDetailRow {

    private Long conversationId;
    private Long userId;
    private String role;
    private String status;
    private Boolean muted;
    private String displayName;
    private String avatarUrl;
    private Boolean anonymous;
    private String permissionsJson;
    private String roomProfileJson;
    private LocalDateTime joinedAt;
    private String userName;
    private String userEmail;
}
