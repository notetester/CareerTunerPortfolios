package com.careertuner.collaboration.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long conversationId,
        UserBriefResponse sender,
        boolean mine,
        String kind,
        String content,
        List<MessageAttachmentResponse> attachments,
        List<SharedPostingResponse> sharedPostings,
        LocalDateTime createdAt,
        /** 뷰어가 차단한 발신자의 메시지(content.roomMessage) 톰스톤 여부. 기본 false. */
        boolean blocked
) {
}
