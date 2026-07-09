package com.careertuner.admin.ticket.dto;

import java.util.List;

import com.careertuner.support.dto.TicketAttachmentView;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTicketMessageResponse {

    private Long id;
    private String who;
    private String name;
    private String time;
    private String text;
    private boolean internal;
    /** 이 메시지에 딸린 첨부(있으면). 다운로드는 /api/admin/tickets/attachments/{id}/content. */
    private List<TicketAttachmentView> attachments;
}
