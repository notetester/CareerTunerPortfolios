package com.careertuner.admin.superadmin.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminPermissionAuditRow {
    private Long id;
    private Long actorUserId;
    private String actorEmail;
    private Long targetUserId;
    private String targetEmail;
    private String actionType;
    private String permissionCode;
    private String groupCode;
    private String reason;
    private LocalDateTime createdAt;
}
