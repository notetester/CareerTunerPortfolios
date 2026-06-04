package com.careertuner.auth.dto;

import com.careertuner.user.domain.User;

public record MeResponse(
        Long id,
        String email,
        String name,
        String role,
        String userType,
        boolean emailVerified,
        String plan,
        int credit) {

    public static MeResponse from(User u) {
        return new MeResponse(u.getId(), u.getEmail(), u.getName(), u.getRole(),
                u.getUserType(), u.isEmailVerified(), u.getPlan(), u.getCredit());
    }
}
