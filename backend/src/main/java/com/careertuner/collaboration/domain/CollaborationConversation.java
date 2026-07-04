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
public class CollaborationConversation {

    private Long id;
    private String type;
    private Long userLowId;
    private Long userHighId;
    private String title;
    private String description;
    private String profileImageUrl;
    private String passwordHash;
    private Integer maxMembers;
    private String joinPolicy;
    private String invitePolicy;
    private Boolean anonymousAllowed;
    private Boolean anonymousOnly;
    private Boolean roomProfileRequired;
    private String settingsJson;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
