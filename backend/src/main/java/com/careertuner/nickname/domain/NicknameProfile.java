package com.careertuner.nickname.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 복수 닉네임 프로필 VO (user_nickname_profile).
 *
 * <p>닉네임은 전역 UNIQUE. 제재/신고/차단은 계정(user_id) 단위로 귀속되고,
 * 이 프로필은 커뮤니티/채팅에서 노출되는 표시 계층이다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NicknameProfile {

    private Long id;
    private Long userId;
    private String nickname;
    private Long avatarFileId;
    private String bio;
    private boolean isDefault;
    private String status;       // ACTIVE/HIDDEN
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
