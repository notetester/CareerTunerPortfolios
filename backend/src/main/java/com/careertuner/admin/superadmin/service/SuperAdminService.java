package com.careertuner.admin.superadmin.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.common.security.AdminAccountMutationGuard;
import com.careertuner.admin.common.security.AdminAccountState;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.superadmin.dto.AdminAccountRow;
import com.careertuner.admin.superadmin.dto.AdminGroupRequest;
import com.careertuner.admin.superadmin.dto.AdminPermissionAuditRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionGroupRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionPolicyRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionRequest;
import com.careertuner.admin.superadmin.dto.AdminPermissionRequestRow;
import com.careertuner.admin.superadmin.mapper.PermissionRequestMapper;
import com.careertuner.admin.superadmin.mapper.SuperAdminMapper;
import com.careertuner.auth.mapper.AuthMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private static final Set<String> ADMIN_ROLES = Set.of("USER", "ADMIN", "SUPER_ADMIN");
    private static final Map<String, List<String>> ROLE_PERMISSION_CODES = Map.of(
            "USER", List.of(),
            "ADMIN", List.of(
                    "MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN",
                    "USER_READ", "PROFILE_READ", "CONSENT_READ", "AI_USAGE_READ", "SECURITY_LOG_READ",
                    "USER_STATUS_WRITE", "BLOCK_MANAGE", "EMAIL_AUDIT_READ", "ADMIN_AUDIT_READ",
                    "BILLING_READ", "BILLING_WRITE", "CONTENT_MANAGE", "AI_OPERATION_MANAGE",
                    "ANALYSIS_READ", "INTERVIEW_READ"),
            "SUPER_ADMIN", List.of("USER_READ", "PROFILE_READ", "CONSENT_READ", "AI_USAGE_READ", "SECURITY_LOG_READ",
                    "USER_STATUS_WRITE", "BLOCK_MANAGE", "EMAIL_AUDIT_READ", "ADMIN_AUDIT_READ",
                    "BILLING_READ", "BILLING_WRITE", "CONTENT_MANAGE", "AI_OPERATION_MANAGE",
                    "ANALYSIS_READ", "INTERVIEW_READ", "MEMBER_ADMIN", "AI_ADMIN", "BILLING_ADMIN",
                    "CONTENT_ADMIN", "AUDIT_ADMIN", "POLICY_ADMIN", "POLICY_MANAGE", "ADMIN_PERMISSION_MANAGE")
    );
    private static final Map<String, List<String>> ROLE_GROUP_CODES = Map.of(
            "USER", List.of(),
            "ADMIN", List.of("ADMIN_OPERATOR", "SECURITY_OPERATOR", "MEMBER_ADMIN", "AI_ADMIN",
                    "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN"),
            "SUPER_ADMIN", List.of("ADMIN_OPERATOR", "SECURITY_OPERATOR", "MEMBER_ADMIN", "AI_ADMIN",
                     "BILLING_ADMIN", "CONTENT_ADMIN", "AUDIT_ADMIN", "POLICY_ADMIN", "SUPER_ADMIN_GROUP")
    );
    private static final Set<String> PERMISSION_CATALOG = ROLE_PERMISSION_CODES.values().stream()
            .flatMap(List::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> GROUP_CATALOG = ROLE_GROUP_CODES.values().stream()
            .flatMap(List::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Map<String, String> ACCOUNT_SORT_COLUMNS = Map.of(
            "id", "id",
            "email", "email",
            "name", "name",
            "role", "role",
            "status", "status",
            "lastLoginAt", "last_login_at",
            "createdAt", "created_at"
    );
    private static final Map<String, String> AUDIT_SORT_COLUMNS = Map.of(
            "createdAt", "a.created_at",
            "actionType", "a.action_type",
            "actorEmail", "actor.email",
            "targetEmail", "target.email",
            "permissionCode", "a.permission_code",
            "groupCode", "a.group_code"
    );

    private final SuperAdminMapper mapper;
    private final AdminActionLogService actionLogService;
    private final PermissionRequestMapper requestMapper;
    private final AuthMapper authMapper;
    private final AdminAccountMutationGuard accountMutationGuard;

    @Transactional(readOnly = true)
    public List<AdminAccountRow> admins(AuthUser authUser, String keyword, String sortBy, String sortDir, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        List<AdminAccountRow> rows = mapper.findAdmins(blankToNull(keyword), normalizeAccountSortColumn(sortBy),
                normalizeSortDir(sortDir), normalizeLimit(limit));
        rows.forEach(this::hydrateAssignments);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountRow> searchUsers(AuthUser authUser, String keyword, String sortBy, String sortDir, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.searchUsers(blankToNull(keyword), normalizeAccountSortColumn(sortBy),
                normalizeSortDir(sortDir), normalizeLimit(limit));
    }

    @Transactional(readOnly = true)
    public AdminAccountRow admin(AuthUser authUser, Long userId) {
        AdminAccess.requireSuperAdmin(authUser);
        AdminAccountRow row = findUser(userId);
        hydrateAssignments(row);
        return row;
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionPolicyRow> permissions(AuthUser authUser) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findPermissions();
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionGroupRow> groups(AuthUser authUser) {
        AdminAccess.requireSuperAdmin(authUser);
        List<AdminPermissionGroupRow> rows = mapper.findGroups();
        rows.forEach(this::hydrateGroupPermissions);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionAuditRow> audit(AuthUser authUser, Long userId, String sortBy, String sortDir, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findAudit(userId, normalizeAuditSortColumn(sortBy), normalizeSortDir(sortDir), normalizeLimit(limit));
    }

    @Transactional
    public AdminAccountRow updateRole(AuthUser authUser, Long userId, String role, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        String nextRole = normalizeRole(role);
        AdminAccountState locked = accountMutationGuard.validateRoleChange(authUser, userId, nextRole);
        if (locked == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        AdminAccountRow before = findUser(userId);
        mapper.updateRole(userId, nextRole);
        revokeAssignmentsOutsideRole(userId, nextRole);
        authMapper.revokeAllForUser(userId);
        mapper.insertAudit(authUser.id(), userId, "ROLE_UPDATED", null, null, blankToNull(reason));
        actionLogService.record(authUser, userId, "ADMIN_ROLE_UPDATED", "ADMIN_USER",
                "{\"role\":\"%s\"}".formatted(before.getRole()),
                "{\"role\":\"%s\"}".formatted(nextRole),
                reason);
        return admin(authUser, userId);
    }

    @Transactional
    public void createPermission(AuthUser authUser, AdminPermissionRequest request) {
        AdminAccess.requireSuperAdmin(authUser);
        String code = normalizeCode(request.code());
        if (!PERMISSION_CATALOG.contains(code)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정의되지 않은 관리자 권한 코드는 생성할 수 없습니다.");
        }
        if (mapper.updatePermissionMetadata(code, request.displayName().trim(),
                blankToNull(request.description()), authUser.id()) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기본 관리자 권한 카탈로그 항목을 찾을 수 없습니다.");
        }
        mapper.insertAudit(authUser.id(), null, "PERMISSION_POLICY_METADATA_UPDATED", code, null, null);
        actionLogService.record(authUser, null, "PERMISSION_POLICY_METADATA_UPDATED", "ADMIN_PERMISSION",
                null, "{\"permissionCode\":\"%s\"}".formatted(code), request.description());
    }

    @Transactional
    public void togglePermission(AuthUser authUser, String code, boolean active) {
        AdminAccess.requireSuperAdmin(authUser);
        String normalized = normalizeCode(code);
        mapper.togglePermission(normalized, active, authUser.id());
        mapper.insertAudit(authUser.id(), null, active ? "PERMISSION_POLICY_ENABLED" : "PERMISSION_POLICY_DISABLED",
                normalized, null, null);
        actionLogService.record(authUser, null, active ? "PERMISSION_POLICY_ENABLED" : "PERMISSION_POLICY_DISABLED",
                "ADMIN_PERMISSION", null, "{\"active\":%s}".formatted(active), normalized);
    }

    @Transactional
    public void createGroup(AuthUser authUser, AdminGroupRequest request) {
        AdminAccess.requireSuperAdmin(authUser);
        String code = normalizeCode(request.code());
        if (!GROUP_CATALOG.contains(code)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정의되지 않은 관리자 권한 그룹은 생성할 수 없습니다.");
        }
        if (mapper.updateGroupMetadata(code, request.displayName().trim(),
                blankToNull(request.description()), authUser.id()) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "기본 관리자 권한 그룹을 찾을 수 없습니다.");
        }
        mapper.insertAudit(authUser.id(), null, "PERMISSION_GROUP_METADATA_UPDATED", null, code, null);
        actionLogService.record(authUser, null, "PERMISSION_GROUP_METADATA_UPDATED", "ADMIN_GROUP",
                null, "{\"groupCode\":\"%s\"}".formatted(code), request.description());
    }

    @Transactional
    public void toggleGroup(AuthUser authUser, String code, boolean active) {
        AdminAccess.requireSuperAdmin(authUser);
        String normalized = normalizeCode(code);
        mapper.toggleGroup(normalized, active, authUser.id());
        mapper.insertAudit(authUser.id(), null, active ? "PERMISSION_GROUP_ENABLED" : "PERMISSION_GROUP_DISABLED",
                null, normalized, null);
        actionLogService.record(authUser, null, active ? "PERMISSION_GROUP_ENABLED" : "PERMISSION_GROUP_DISABLED",
                "ADMIN_GROUP", null, "{\"active\":%s}".formatted(active), normalized);
    }

    @Transactional
    public void addGroupItem(AuthUser authUser, String groupCode, String permissionCode) {
        AdminAccess.requireSuperAdmin(authUser);
        String group = normalizeCode(groupCode);
        String permission = normalizeCode(permissionCode);
        mapper.addGroupItem(group, permission, authUser.id());
        mapper.insertAudit(authUser.id(), null, "GROUP_PERMISSION_ADDED", permission, group, null);
        actionLogService.record(authUser, null, "GROUP_PERMISSION_ADDED", "ADMIN_GROUP",
                null, "{\"groupCode\":\"%s\",\"permissionCode\":\"%s\"}".formatted(group, permission), null);
    }

    @Transactional
    public void removeGroupItem(AuthUser authUser, String groupCode, String permissionCode) {
        AdminAccess.requireSuperAdmin(authUser);
        String group = normalizeCode(groupCode);
        String permission = normalizeCode(permissionCode);
        mapper.removeGroupItem(group, permission);
        mapper.insertAudit(authUser.id(), null, "GROUP_PERMISSION_REMOVED", permission, group, null);
        actionLogService.record(authUser, null, "GROUP_PERMISSION_REMOVED", "ADMIN_GROUP",
                "{\"groupCode\":\"%s\",\"permissionCode\":\"%s\"}".formatted(group, permission), null, null);
    }

    @Transactional
    public AdminAccountRow grantPermission(AuthUser authUser, Long userId, String permissionCode, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        findUser(userId);
        String permission = normalizeCode(permissionCode);
        validatePermissionAllowedForUser(userId, permission);
        mapper.grantPermission(userId, permission, authUser.id());
        mapper.insertAudit(authUser.id(), userId, "PERMISSION_GRANTED", permission, null, blankToNull(reason));
        actionLogService.record(authUser, userId, "PERMISSION_GRANTED", "ADMIN_USER",
                null, "{\"permissionCode\":\"%s\"}".formatted(permission), reason);
        return admin(authUser, userId);
    }

    @Transactional
    public AdminAccountRow revokePermission(AuthUser authUser, Long userId, String permissionCode, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        findUser(userId);
        String permission = normalizeCode(permissionCode);
        mapper.revokePermission(userId, permission);
        mapper.insertAudit(authUser.id(), userId, "PERMISSION_REVOKED", permission, null, blankToNull(reason));
        actionLogService.record(authUser, userId, "PERMISSION_REVOKED", "ADMIN_USER",
                "{\"permissionCode\":\"%s\"}".formatted(permission), null, reason);
        return admin(authUser, userId);
    }

    @Transactional
    public AdminAccountRow assignGroup(AuthUser authUser, Long userId, String groupCode, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        findUser(userId);
        String group = normalizeCode(groupCode);
        validateGroupAllowedForUser(userId, group);
        mapper.assignGroup(userId, group, authUser.id());
        mapper.insertAudit(authUser.id(), userId, "GROUP_ASSIGNED", null, group, blankToNull(reason));
        actionLogService.record(authUser, userId, "GROUP_ASSIGNED", "ADMIN_USER",
                null, "{\"groupCode\":\"%s\"}".formatted(group), reason);
        return admin(authUser, userId);
    }

    @Transactional
    public AdminAccountRow revokeGroup(AuthUser authUser, Long userId, String groupCode, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        findUser(userId);
        String group = normalizeCode(groupCode);
        mapper.revokeGroup(userId, group);
        mapper.insertAudit(authUser.id(), userId, "GROUP_REVOKED", null, group, blankToNull(reason));
        actionLogService.record(authUser, userId, "GROUP_REVOKED", "ADMIN_USER",
                "{\"groupCode\":\"%s\"}".formatted(group), null, reason);
        return admin(authUser, userId);
    }

    /* ── 권한 요청/승인 워크플로우 ── */

    /** 관리자 권한 요청 생성(대상 관리자에게 부여할 권한들). 관리자면 요청 가능, 승인은 슈퍼관리자. */
    @Transactional
    public void requestPermissions(AuthUser authUser, Long userId, List<String> permissionCodes, String description) {
        AdminAccess.requireAdmin(authUser);
        findUser(userId);
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청할 권한을 선택해 주세요.");
        }
        for (String code : permissionCodes) {
            String permission = normalizeCode(code);
            validatePermissionAllowedForUser(userId, permission);
            requestMapper.insertPermissionRequest(userId, permission, blankToNull(description), authUser.id());
        }
        actionLogService.record(authUser, userId, "PERMISSION_REQUESTED", "ADMIN_USER",
                null, "{\"count\":%d}".formatted(permissionCodes.size()), description);
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionRequestRow> permissionRequests(AuthUser authUser, String status, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        int lim = limit <= 0 || limit > 500 ? 200 : limit;
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return requestMapper.findRequests(normalizedStatus, lim);
    }

    /** 요청 승인 → 실제 권한 부여 + 요청 상태 APPROVED. */
    @Transactional
    public void approvePermissionRequest(AuthUser authUser, Long requestId) {
        AdminAccess.requireSuperAdmin(authUser);
        AdminPermissionRequestRow request = requireRequest(requestId);
        validatePermissionAllowedForUser(request.userId(), request.permissionCode());
        mapper.grantPermission(request.userId(), request.permissionCode(), authUser.id());
        int updated = requestMapper.updateRequestStatus(requestId, "APPROVED", authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 요청입니다.");
        }
        mapper.insertAudit(authUser.id(), request.userId(), "PERMISSION_GRANTED", request.permissionCode(), null, "요청 승인");
        actionLogService.record(authUser, request.userId(), "PERMISSION_REQUEST_APPROVED", "ADMIN_USER",
                null, "{\"permissionCode\":\"%s\"}".formatted(request.permissionCode()), null);
    }

    @Transactional
    public void rejectPermissionRequest(AuthUser authUser, Long requestId, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        AdminPermissionRequestRow request = requireRequest(requestId);
        int updated = requestMapper.updateRequestStatus(requestId, "REJECTED", authUser.id());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 처리된 요청입니다.");
        }
        actionLogService.record(authUser, request.userId(), "PERMISSION_REQUEST_REJECTED", "ADMIN_USER",
                "{\"permissionCode\":\"%s\"}".formatted(request.permissionCode()), null, reason);
    }

    /* ── 일괄 처리 ── */

    /** 다중 관리자에게 권한 일괄 부여. */
    @Transactional
    public int bulkGrantPermissions(AuthUser authUser, List<Long> userIds, List<String> permissionCodes, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        if (userIds == null || userIds.isEmpty() || permissionCodes == null || permissionCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 관리자와 권한을 선택해 주세요.");
        }
        int granted = 0;
        for (Long userId : userIds.stream().distinct().toList()) {
            findUser(userId);
            for (String code : permissionCodes) {
                String permission = normalizeCode(code);
                validatePermissionAllowedForUser(userId, permission);
                mapper.grantPermission(userId, permission, authUser.id());
                mapper.insertAudit(authUser.id(), userId, "PERMISSION_GRANTED", permission, null, blankToNull(reason));
                granted++;
            }
        }
        actionLogService.record(authUser, null, "PERMISSION_BULK_GRANTED", "ADMIN_USER",
                null, "{\"users\":%d,\"perms\":%d}".formatted(userIds.size(), permissionCodes.size()), reason);
        return granted;
    }

    /** 다중 관리자를 일괄 관리자 해제(role=USER + 권한·그룹 전부 회수). 본인 제외. */
    @Transactional
    public int bulkRevokeAdmins(AuthUser authUser, List<Long> userIds, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 관리자를 선택해 주세요.");
        }
        int revoked = 0;
        for (Long userId : userIds.stream().distinct().toList()) {
            if (userId.equals(authUser.id())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "본인 관리자 역할을 변경할 수 없습니다.");
            }
            AdminAccountState locked = accountMutationGuard.validateRoleChange(authUser, userId, "USER");
            if (locked == null) {
                continue;
            }
            mapper.updateRole(userId, "USER");
            mapper.revokeAllPermissionsForUser(userId);
            mapper.revokeAllGroupsForUser(userId);
            authMapper.revokeAllForUser(userId);
            mapper.insertAudit(authUser.id(), userId, "ADMIN_REVOKED", null, null, blankToNull(reason));
            revoked++;
        }
        actionLogService.record(authUser, null, "ADMIN_BULK_REVOKED", "ADMIN_USER",
                null, "{\"users\":%d}".formatted(revoked), reason);
        return revoked;
    }

    private AdminPermissionRequestRow requireRequest(Long requestId) {
        AdminPermissionRequestRow row = requestMapper.findRequestById(requestId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "권한 요청을 찾을 수 없습니다.");
        }
        return row;
    }

    private AdminAccountRow findUser(Long userId) {
        AdminAccountRow row = mapper.findAdmin(userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        return row;
    }

    private void hydrateAssignments(AdminAccountRow row) {
        row.setPermissions(mapper.findUserPermissions(row.getId()));
        row.setGroups(mapper.findUserGroups(row.getId()));
    }

    private void hydrateGroupPermissions(AdminPermissionGroupRow row) {
        row.setPermissions(mapper.findGroupPermissions(row.getGroupCode()));
    }

    private void revokeAssignmentsOutsideRole(Long userId, String role) {
        List<String> allowedPermissions = ROLE_PERMISSION_CODES.getOrDefault(role, List.of());
        if (allowedPermissions.isEmpty()) {
            mapper.revokeAllPermissionsForUser(userId);
        } else {
            mapper.revokePermissionsNotIn(userId, allowedPermissions);
        }

        List<String> allowedGroups = ROLE_GROUP_CODES.getOrDefault(role, List.of());
        if (allowedGroups.isEmpty()) {
            mapper.revokeAllGroupsForUser(userId);
        } else {
            mapper.revokeGroupsNotIn(userId, allowedGroups);
        }
    }

    private void validatePermissionAllowedForUser(Long userId, String permissionCode) {
        AdminAccountRow user = findUser(userId);
        List<String> allowed = ROLE_PERMISSION_CODES.getOrDefault(user.getRole(), List.of());
        if (!allowed.contains(permissionCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 역할에 부여할 수 없는 메뉴 권한입니다.");
        }
    }

    private void validateGroupAllowedForUser(Long userId, String groupCode) {
        AdminAccountRow user = findUser(userId);
        List<String> allowed = ROLE_GROUP_CODES.getOrDefault(user.getRole(), List.of());
        if (!allowed.contains(groupCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 역할에 부여할 수 없는 권한 그룹입니다.");
        }
    }

    private static String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!ADMIN_ROLES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않는 관리자 역할입니다.");
        }
        return normalized;
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "권한 코드가 필요합니다.");
        }
        return code.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 300);
    }

    private static String normalizeAccountSortColumn(String sortBy) {
        String normalized = blankToNull(sortBy);
        return normalized == null ? "created_at" : ACCOUNT_SORT_COLUMNS.getOrDefault(normalized, "created_at");
    }

    private static String normalizeAuditSortColumn(String sortBy) {
        String normalized = blankToNull(sortBy);
        return normalized == null ? "a.created_at" : AUDIT_SORT_COLUMNS.getOrDefault(normalized, "a.created_at");
    }

    private static String normalizeSortDir(String sortDir) {
        return "ASC".equalsIgnoreCase(blankToNull(sortDir)) ? "ASC" : "DESC";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
