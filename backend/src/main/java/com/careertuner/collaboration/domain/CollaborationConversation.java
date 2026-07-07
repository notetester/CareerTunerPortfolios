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
    /** 방 프로필 사진(file_asset id). 없으면 null. */
    private Long imageFileId;
    /** 방 공지. */
    private String notice;
    /** 초대 권한 정책 (OWNER_ONLY / MANAGERS / SPECIFIC_MEMBERS / ALL_MEMBERS). */
    private String invitePolicy;
    /** 익명 참가 허용 여부. */
    private Boolean allowAnonymous;
    /** 익명만 참가 가능(실명 참가 불가). */
    private Boolean anonymousOnly;
    private String passwordHash;
    private Integer maxMembers;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
