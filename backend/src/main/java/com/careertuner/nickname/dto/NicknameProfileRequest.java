package com.careertuner.nickname.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 닉네임 프로필 생성/수정 요청. */
public record NicknameProfileRequest(
        @NotBlank @Size(max = 30) String nickname,
        Long avatarFileId,
        @Size(max = 200) String bio) {
}
