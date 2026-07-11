package com.careertuner.admin.superadmin.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class AdminPermissionGroupRow {
    private String groupCode;
    private String displayName;
    private String description;
    private String roleScope;
    private boolean active;
    private long itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AdminPermissionPolicyRow> permissions = new ArrayList<>();
}
