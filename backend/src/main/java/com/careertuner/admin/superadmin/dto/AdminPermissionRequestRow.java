package com.careertuner.admin.superadmin.dto;

import java.time.LocalDateTime;

/** 관리자 권한 요청 1건. */
public record AdminPermissionRequestRow(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String permissionCode,
        String permissionName,
        String description,
        String status,
        Long requestedBy,
        String requestedByName,
        Long decidedBy,
        LocalDateTime decidedAt,
        LocalDateTime createdAt) {
}
