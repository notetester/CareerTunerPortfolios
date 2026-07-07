package com.careertuner.admin.superadmin.dto;

import java.util.List;

/** 권한 요청/승인·일괄 처리 요청 DTO 묶음. */
public final class PermissionGovernanceDtos {

    private PermissionGovernanceDtos() {
    }

    public record PermissionRequestCreate(Long userId, List<String> permissionCodes, String description) {
    }

    public record RejectRequest(String reason) {
    }

    public record BulkGrantRequest(List<Long> userIds, List<String> permissionCodes, String reason) {
    }

    public record BulkRevokeRequest(List<Long> userIds, String reason) {
    }
}
