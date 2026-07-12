package com.careertuner.collaboration.dto;

public record UserBriefResponse(
        /** 익명 또는 탈퇴 계정은 공개 식별자 링크를 만들지 않도록 null. */
        Long id,
        String name,
        String email
) {
}
