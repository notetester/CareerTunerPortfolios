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
public class ConversationSummaryRow {

    private Long id;
    private String type;
    private String title;
    private String description;
    private String profileImageUrl;
    private Boolean locked;
    private Integer memberCount;
    private Boolean joined;
    private Boolean muted;
    private String role;
    private String joinPolicy;
    private String invitePolicy;
    private Boolean anonymousAllowed;
    private Boolean anonymousOnly;
    private Boolean roomProfileRequired;
    private Long peerUserId;
    private String peerName;
    private String peerEmail;
    private LocalDateTime updatedAt;
    private Long latestMessageId;
    private String latestKind;
    private String latestContent;
    private LocalDateTime latestCreatedAt;
    private int unreadCount;
}
