package com.careertuner.support.mapper;

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
}
