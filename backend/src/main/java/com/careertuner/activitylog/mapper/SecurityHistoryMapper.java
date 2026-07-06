package com.careertuner.activitylog.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.activitylog.domain.UserSecurityHistory;

/** 보안 이력 저장 + 관리자 조회. */
@Mapper
public interface SecurityHistoryMapper {

    void insertSecurityHistory(UserSecurityHistory history);

    List<UserSecurityHistory> findSecurityHistories(@Param("keyword") String keyword,
                                                    @Param("eventType") String eventType,
                                                    @Param("eventStage") String eventStage,
                                                    @Param("success") Boolean success,
                                                    @Param("userId") Long userId,
                                                    @Param("from") String from,
                                                    @Param("to") String to,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    int countSecurityHistories(@Param("keyword") String keyword,
                               @Param("eventType") String eventType,
                               @Param("eventStage") String eventStage,
                               @Param("success") Boolean success,
                               @Param("userId") Long userId,
                               @Param("from") String from,
                               @Param("to") String to);
}
