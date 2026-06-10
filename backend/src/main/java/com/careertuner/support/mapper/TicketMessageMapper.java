package com.careertuner.support.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.support.domain.TicketMessage;

@Mapper
public interface TicketMessageMapper {

    void insert(TicketMessage message);

    List<TicketMessage> findByTicketId(@Param("ticketId") Long ticketId);
}
