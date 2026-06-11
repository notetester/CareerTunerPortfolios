package com.careertuner.support.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.support.domain.SupportTicket;

@Mapper
public interface TicketMapper {

    void insert(SupportTicket ticket);

    SupportTicket findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
