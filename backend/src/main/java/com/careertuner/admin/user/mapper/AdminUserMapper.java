package com.careertuner.admin.user.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.user.dto.AdminUserConsentRow;
import com.careertuner.admin.user.dto.AdminUserLoginHistoryRow;
import com.careertuner.admin.user.dto.AdminUserRow;
import com.careertuner.admin.user.dto.AdminUserStatusHistoryRow;

@Mapper
public interface AdminUserMapper {

    List<AdminUserRow> findUsers(@Param("keyword") String keyword,
                                 @Param("status") String status,
                                 @Param("role") String role,
                                 @Param("limit") int limit);

    AdminUserRow findUser(Long id);

    List<AdminUserLoginHistoryRow> findLoginHistory(@Param("userId") Long userId, @Param("limit") int limit);

    List<AdminUserStatusHistoryRow> findStatusHistory(@Param("userId") Long userId, @Param("limit") int limit);

    List<AdminUserConsentRow> findConsents(@Param("userId") Long userId);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("reason") String reason,
                     @Param("blockedUntil") LocalDateTime blockedUntil,
                     @Param("actorUserId") Long actorUserId);

    void insertStatusHistory(@Param("userId") Long userId,
                             @Param("actorUserId") Long actorUserId,
                             @Param("previousStatus") String previousStatus,
                             @Param("newStatus") String newStatus,
                             @Param("reason") String reason,
                             @Param("memo") String memo,
                             @Param("blockedUntil") LocalDateTime blockedUntil);
}
