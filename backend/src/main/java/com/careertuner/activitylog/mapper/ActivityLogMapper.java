package com.careertuner.activitylog.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.activitylog.domain.UserActivityLog;

/** 활동 로그 저장 + 관리자 조회. */
@Mapper
public interface ActivityLogMapper {

    void insertActivityLog(UserActivityLog log);

    List<UserActivityLog> findActivityLogs(@Param("keyword") String keyword,
                                           @Param("domain") String domain,
                                           @Param("activityType") String activityType,
                                           @Param("success") Boolean success,
                                           @Param("userId") Long userId,
                                           @Param("from") String from,
                                           @Param("to") String to,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    int countActivityLogs(@Param("keyword") String keyword,
                          @Param("domain") String domain,
                          @Param("activityType") String activityType,
                          @Param("success") Boolean success,
                          @Param("userId") Long userId,
                          @Param("from") String from,
                          @Param("to") String to);
}
