package com.careertuner.admin.ticket.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.admin.ticket.dto.AdminTicketListResponse;
import com.careertuner.admin.ticket.dto.AdminTicketMessageResponse;

@Mapper
public interface AdminTicketMapper {

    List<AdminTicketListResponse> findAll(@Param("status") String status);

    AdminTicketListResponse findById(@Param("id") Long id);

    Long findUserIdById(@Param("id") Long id);

    List<AdminTicketMessageResponse> findMessages(@Param("ticketId") Long ticketId);

    String findInternalMemo(@Param("ticketId") Long ticketId);

    void updateTicket(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("priority") String priority);

    void insertMessage(@Param("ticketId") Long ticketId,
                       @Param("senderType") String senderType,
                       @Param("senderId") Long senderId,
                       @Param("content") String content,
                       @Param("internal") boolean internal);

    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
