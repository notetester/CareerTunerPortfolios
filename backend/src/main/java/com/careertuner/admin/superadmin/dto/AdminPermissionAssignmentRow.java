package com.careertuner.admin.superadmin.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminPermissionAssignmentRow {
    private Long id;
    private Long userId;
    private String permissionCode;
    private String displayName;
    private Long grantedBy;
    private LocalDateTime grantedAt;
}
