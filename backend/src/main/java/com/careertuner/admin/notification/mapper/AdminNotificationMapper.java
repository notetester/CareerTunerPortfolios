package com.careertuner.admin.notification.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.notification.dto.AdminNotificationResponse;

@Mapper
public interface AdminNotificationMapper {

    List<AdminNotificationResponse> findRecent(@Param("limit") int limit);
}
