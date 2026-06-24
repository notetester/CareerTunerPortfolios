package com.careertuner.admin.superadmin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.superadmin.dto.AdminAccountRow;
import com.careertuner.admin.superadmin.dto.AdminGroupAssignmentRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionAssignmentRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionAuditRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionGroupRow;
import com.careertuner.admin.superadmin.dto.AdminPermissionPolicyRow;

@Mapper
public interface SuperAdminMapper {

    List<AdminAccountRow> findAdmins(@Param("keyword") String keyword, @Param("limit") int limit);

    AdminAccountRow findAdmin(Long userId);

    List<AdminAccountRow> searchUsers(@Param("keyword") String keyword, @Param("limit") int limit);

    List<AdminPermissionPolicyRow> findPermissions();

    List<AdminPermissionGroupRow> findGroups();

    List<AdminPermissionAssignmentRow> findUserPermissions(Long userId);

    List<AdminGroupAssignmentRow> findUserGroups(Long userId);

    List<AdminPermissionAuditRow> findAudit(@Param("userId") Long userId, @Param("limit") int limit);

    void updateRole(@Param("userId") Long userId, @Param("role") String role);

    void insertPermission(@Param("code") String code, @Param("displayName") String displayName,
                          @Param("description") String description, @Param("actorId") Long actorId);

    void togglePermission(@Param("code") String code, @Param("active") boolean active, @Param("actorId") Long actorId);

    void insertGroup(@Param("code") String code, @Param("displayName") String displayName,
                     @Param("description") String description, @Param("actorId") Long actorId);

    void toggleGroup(@Param("code") String code, @Param("active") boolean active, @Param("actorId") Long actorId);

    void addGroupItem(@Param("groupCode") String groupCode, @Param("permissionCode") String permissionCode,
                      @Param("actorId") Long actorId);

    void removeGroupItem(@Param("groupCode") String groupCode, @Param("permissionCode") String permissionCode);

    void grantPermission(@Param("userId") Long userId, @Param("permissionCode") String permissionCode,
                         @Param("actorId") Long actorId);

    void revokePermission(@Param("userId") Long userId, @Param("permissionCode") String permissionCode);

    void assignGroup(@Param("userId") Long userId, @Param("groupCode") String groupCode,
                     @Param("actorId") Long actorId);

    void revokeGroup(@Param("userId") Long userId, @Param("groupCode") String groupCode);

    void insertAudit(@Param("actorId") Long actorId, @Param("targetUserId") Long targetUserId,
                     @Param("actionType") String actionType, @Param("permissionCode") String permissionCode,
                     @Param("groupCode") String groupCode, @Param("reason") String reason);
}
