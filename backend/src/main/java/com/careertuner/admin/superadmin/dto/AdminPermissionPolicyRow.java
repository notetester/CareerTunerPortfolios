package com.careertuner.admin.superadmin.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminPermissionPolicyRow {
    private String permissionCode;
    private String displayName;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
