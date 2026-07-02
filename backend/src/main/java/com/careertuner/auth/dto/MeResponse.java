package com.careertuner.auth.dto;

import java.util.List;

import com.careertuner.user.domain.User;

public record MeResponse(
        Long id,
        String email,
        String name,
        String role,
        String userType,
        boolean emailVerified,
        String plan,
        int credit,
        List<String> permissions,
        List<String> permissionGroups) {

    public static MeResponse from(User u) {
        return new MeResponse(u.getId(), u.getEmail(), u.getName(), u.getRole(),
                u.getUserType(), u.isEmailVerified(), u.getPlan(), u.getCredit(), List.of(), List.of());
    }

    public static MeResponse from(User u, List<String> permissions, List<String> permissionGroups) {
        return new MeResponse(u.getId(), u.getEmail(), u.getName(), u.getRole(),
                u.getUserType(), u.isEmailVerified(), u.getPlan(), u.getCredit(),
                permissions == null ? List.of() : List.copyOf(permissions),
                permissionGroups == null ? List.of() : List.copyOf(permissionGroups));
    }
}
