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
    private Long imageFileId;
    private String notice;
    private Boolean locked;
    /** 뷰어의 방 내 role (없으면 null). 방 설정 진입 게이팅용. */
    private String myRole;
    private Integer memberCount;
    private Boolean joined;
    private Boolean muted;
    private Long peerUserId;
    private String peerName;
    private String peerEmail;
    private String peerStatus;
    private LocalDateTime updatedAt;
    private Long latestMessageId;
    private String latestKind;
    private String latestContent;
    private LocalDateTime latestCreatedAt;
    private int unreadCount;
}
