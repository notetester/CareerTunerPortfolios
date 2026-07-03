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
public class UserChatProfile {

    private Long id;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String description;
    private boolean defaultProfile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
