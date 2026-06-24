package com.careertuner.admin.superadmin.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.common.AdminAccess;
import com.careertuner.admin.ops.service.AdminActionLogService;
import com.careertuner.admin.superadmin.dto.AdminAccountRow;
import com.careertuner.admin.superadmin.dto.AdminGroupRequest;
import com.careertuner.admin.superadmin.dto.AdminPermissionAuditRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionGroupRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionPolicyRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionRequest;
import com.careertuner.admin.superadmin.mapper.SuperAdminMapper;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private static final Set<String> ADMIN_ROLES = Set.of("USER", "ADMIN", "SUPER_ADMIN");

    private final SuperAdminMapper mapper;
    private final AdminActionLogService actionLogService;

    @Transactional(readOnly = true)
    public List<AdminAccountRow> admins(AuthUser authUser, String keyword, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        List<AdminAccountRow> rows = mapper.findAdmins(blankToNull(keyword), normalizeLimit(limit));
        rows.forEach(this::hydrateAssignments);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountRow> searchUsers(AuthUser authUser, String keyword, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.searchUsers(blankToNull(keyword), normalizeLimit(limit));
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
        return mapper.findGroups();
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionAuditRow> audit(AuthUser authUser, Long userId, int limit) {
        AdminAccess.requireSuperAdmin(authUser);
        return mapper.findAudit(userId, normalizeLimit(limit));
    }

    @Transactional
    public AdminAccountRow updateRole(AuthUser authUser, Long userId, String role, String reason) {
        AdminAccess.requireSuperAdmin(authUser);
        AdminAccountRow before = findUser(userId);
        String nextRole = normalizeRole(role);
        mapper.updateRole(userId, nextRole);
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
        mapper.insertPermission(normalizeCode(request.code()), request.displayName().trim(),
                blankToNull(request.description()), authUser.id());
        mapper.insertAudit(authUser.id(), null, "PERMISSION_POLICY_CREATED", normalizeCode(request.code()), null, null);
        actionLogService.record(authUser, null, "PERMISSION_POLICY_CREATED", "ADMIN_PERMISSION",
                null, "{\"permissionCode\":\"%s\"}".formatted(normalizeCode(request.code())), request.description());
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
        mapper.insertGroup(normalizeCode(request.code()), request.displayName().trim(),
                blankToNull(request.description()), authUser.id());
        mapper.insertAudit(authUser.id(), null, "PERMISSION_GROUP_CREATED", null, normalizeCode(request.code()), null);
        actionLogService.record(authUser, null, "PERMISSION_GROUP_CREATED", "ADMIN_GROUP",
                null, "{\"groupCode\":\"%s\"}".formatted(normalizeCode(request.code())), request.description());
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
