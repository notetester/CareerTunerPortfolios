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

    List<AdminAccountRow> findAdmins(@Param("keyword") String keyword,
                                     @Param("sortColumn") String sortColumn,
                                     @Param("sortDir") String sortDir,
                                     @Param("limit") int limit);

    AdminAccountRow findAdmin(Long userId);

    List<AdminAccountRow> searchUsers(@Param("keyword") String keyword,
                                      @Param("sortColumn") String sortColumn,
                                      @Param("sortDir") String sortDir,
                                      @Param("limit") int limit);

    List<AdminPermissionPolicyRow> findPermissions();

    List<AdminPermissionGroupRow> findGroups();

    List<AdminPermissionPolicyRow> findGroupPermissions(String groupCode);

    List<AdminPermissionAssignmentRow> findUserPermissions(Long userId);

    List<AdminGroupAssignmentRow> findUserGroups(Long userId);

    List<AdminPermissionAuditRow> findAudit(@Param("userId") Long userId,
                                            @Param("sortColumn") String sortColumn,
                                            @Param("sortDir") String sortDir,
                                            @Param("limit") int limit);

    void updateRole(@Param("userId") Long userId, @Param("role") String role);

    int updatePermissionMetadata(@Param("code") String code, @Param("displayName") String displayName,
                                 @Param("description") String description, @Param("actorId") Long actorId);

    void togglePermission(@Param("code") String code, @Param("active") boolean active, @Param("actorId") Long actorId);

    int updateGroupMetadata(@Param("code") String code, @Param("displayName") String displayName,
                            @Param("description") String description, @Param("actorId") Long actorId);

    void toggleGroup(@Param("code") String code, @Param("active") boolean active, @Param("actorId") Long actorId);

    void addGroupItem(@Param("groupCode") String groupCode, @Param("permissionCode") String permissionCode,
                      @Param("actorId") Long actorId);

    void removeGroupItem(@Param("groupCode") String groupCode, @Param("permissionCode") String permissionCode);

    void grantPermission(@Param("userId") Long userId, @Param("permissionCode") String permissionCode,
                         @Param("actorId") Long actorId);

    void revokePermission(@Param("userId") Long userId, @Param("permissionCode") String permissionCode);

    void revokeAllPermissionsForUser(@Param("userId") Long userId);

    void revokePermissionsNotIn(@Param("userId") Long userId, @Param("permissionCodes") List<String> permissionCodes);

    void assignGroup(@Param("userId") Long userId, @Param("groupCode") String groupCode,
                     @Param("actorId") Long actorId);

    void revokeGroup(@Param("userId") Long userId, @Param("groupCode") String groupCode);

    void revokeAllGroupsForUser(@Param("userId") Long userId);

    void revokeGroupsNotIn(@Param("userId") Long userId, @Param("groupCodes") List<String> groupCodes);

    void insertAudit(@Param("actorId") Long actorId, @Param("targetUserId") Long targetUserId,
                     @Param("actionType") String actionType, @Param("permissionCode") String permissionCode,
                     @Param("groupCode") String groupCode, @Param("reason") String reason);
}
