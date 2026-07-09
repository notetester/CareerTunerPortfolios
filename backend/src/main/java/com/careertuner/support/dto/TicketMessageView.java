package com.careertuner.support.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.support.domain.TicketMessage;

/** 사용자에게 노출하는 문의 메시지 한 줄. 내부 메모(is_internal)는 포함하지 않는다. */
public record TicketMessageView(
        Long id,
        String senderType,
        String content,
        LocalDateTime createdAt,
        List<TicketAttachmentView> attachments
) {
    public static TicketMessageView from(TicketMessage message, List<TicketAttachmentView> attachments) {
        return new TicketMessageView(
                message.getId(),
                message.getSenderType(),
                message.getContent(),
                message.getCreatedAt(),
                attachments);
    }
}
