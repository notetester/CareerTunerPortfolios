package com.careertuner.support.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.support.domain.SupportTicket;

@Mapper
public interface TicketMapper {

    void insert(SupportTicket ticket);

    SupportTicket findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<SupportTicket> findAllByUserId(@Param("userId") Long userId);

    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 문의 rate-limit — since 이후 해당 사용자가 생성한 문의 수(도배 방지 집계). */
    int countRecentByUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
