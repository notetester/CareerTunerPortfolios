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
        LocalDateTime createdAt
) {
}
