package com.careertuner.admin.superadmin.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class AdminAccountRow {
    private Long id;
    private String email;
    private String name;
    private String role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private List<AdminPermissionAssignmentRow> permissions = List.of();
    private List<AdminGroupAssignmentRow> groups = List.of();
}
