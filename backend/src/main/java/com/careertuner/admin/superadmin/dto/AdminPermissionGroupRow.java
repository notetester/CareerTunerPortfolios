package com.careertuner.admin.superadmin.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AdminPermissionGroupRow {
    private String groupCode;
    private String displayName;
    private String description;
    private boolean active;
    private long itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
