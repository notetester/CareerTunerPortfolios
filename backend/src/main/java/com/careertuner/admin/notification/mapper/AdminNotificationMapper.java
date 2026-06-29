package com.careertuner.admin.notification.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.notification.dto.AdminNotificationResponse;

@Mapper
public interface AdminNotificationMapper {

    List<AdminNotificationResponse> findRecent(@Param("limit") int limit);

    /** 전체 알림 수. */
    long countAll();

    /** 읽음 처리된 전체 알림 수. */
    long countRead();

    /** type별 발송/읽음 수(전체). 컬럼: type, sent, read. */
    List<Map<String, Object>> countByType();

    /** 최근 7일 일자별 발송 수. 컬럼: day(yyyy-MM-dd), cnt. */
    List<Map<String, Object>> countByDayLast7();
}
