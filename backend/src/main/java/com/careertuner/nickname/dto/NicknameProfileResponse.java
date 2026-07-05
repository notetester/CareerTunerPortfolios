package com.careertuner.nickname.dto;

import java.time.LocalDateTime;

import com.careertuner.nickname.domain.NicknameProfile;

/** 닉네임 프로필 응답. */
public record NicknameProfileResponse(
        Long id,
        Long userId,
        String nickname,
        Long avatarFileId,
        String bio,
        boolean isDefault,
        String status,
        LocalDateTime updatedAt) {

    public static NicknameProfileResponse from(NicknameProfile profile) {
        return new NicknameProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getNickname(),
                profile.getAvatarFileId(),
                profile.getBio(),
                profile.isDefault(),
                profile.getStatus(),
                profile.getUpdatedAt());
    }
}
