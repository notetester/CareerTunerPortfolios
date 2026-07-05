package com.careertuner.admin.superadmin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.superadmin.dto.AdminPermissionRequestRow;

/** 관리자 권한 요청/승인 워크플로우 매퍼. */
@Mapper
public interface PermissionRequestMapper {

    void insertPermissionRequest(@Param("userId") Long userId,
                                 @Param("permissionCode") String permissionCode,
                                 @Param("description") String description,
                                 @Param("requestedBy") Long requestedBy);

    List<AdminPermissionRequestRow> findRequests(@Param("status") String status, @Param("limit") int limit);

    AdminPermissionRequestRow findRequestById(@Param("id") Long id);

    int updateRequestStatus(@Param("id") Long id,
                            @Param("status") String status,
                            @Param("decidedBy") Long decidedBy);
}
